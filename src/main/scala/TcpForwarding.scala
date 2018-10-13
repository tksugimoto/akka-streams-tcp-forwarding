import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Source, Tcp}
import akka.stream.{ActorMaterializer, FlowShape}
import akka.util.ByteString

import scala.concurrent.Future

object TcpForwarding extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val config = system.settings.config.getConfig("tcp-forwarding")

  val bindHost = config.getString("bind.host")
  val bindPort = config.getInt("bind.port")

  val targetHost = config.getString("target.host")
  val targetPort = config.getInt("target.port")

  val connections: Source[IncomingConnection, Future[ServerBinding]] =
    Tcp().bind(bindHost, bindPort)

  println(s"Started.\n$bindHost:$bindPort -> $targetHost:$targetPort")

  connections runForeach { connection ⇒
    val outgoingConnection = Tcp().outgoingConnection(targetHost, targetPort)
    connection.handleWith(HttpProxy.printFirstLine.via(outgoingConnection))
  }

  object HttpProxy {
    val printFirstLine: Flow[ByteString, ByteString, NotUsed] =
      Flow.fromGraph(GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val CR = ByteString("\r")

        // TODO: 効率化　(1Byteずつにsplitして判定するのは非効率)
        val splitByteString = Flow[ByteString].mapConcat(_.map(ByteString(_)))

        val takeFirstLine = Flow[ByteString]
          .via(splitByteString)
          .takeWhile(_ != CR)
          .fold(ByteString.empty)(_ ++ _)

        val dropFirstLine = Flow[ByteString]
          .via(splitByteString)
          .dropWhile(_ != CR)

        val printLineIfNonEmpty = Flow[ByteString]
          .wireTap { line: ByteString =>
            if (line.nonEmpty) println(line.utf8String)
          }

        val broadcast = builder.add(Broadcast[ByteString](2))
        val concat = builder.add(Concat[ByteString](2))

        broadcast.out(0) ~>
          takeFirstLine ~> printLineIfNonEmpty ~>
          concat.in(0)

        broadcast.out(1) ~>
          dropFirstLine ~>
          concat.in(1)

        FlowShape(broadcast.in, concat.out)
      })
  }
}
