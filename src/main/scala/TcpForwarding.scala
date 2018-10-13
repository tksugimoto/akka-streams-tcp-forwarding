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
    println(s"New connection from: ${connection.remoteAddress}")

    val outgoingConnection = Tcp().outgoingConnection(targetHost, targetPort)

    connection.handleWith(flow.via(outgoingConnection))
  }

  val flow =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val separator = ByteString("\n")

      // TODO: 効率化　(1Byteずつにsplitして判定するのは非効率)
      val splitByteString = Flow[ByteString].mapConcat(_.map(ByteString(_)))

      val takeHead = Flow[ByteString]
        .via(splitByteString)
        .takeWhile(_ != separator)
        .fold(ByteString.empty)(_ ++ _)
        .wireTap { bs =>
          if (bs.nonEmpty) println(s"[head: ${bs.length}] ${bs.utf8String}")
        }

      val dropHead = Flow[ByteString]
        .via(splitByteString)
        .dropWhile(_ != separator)

      val broadcast = b.add(Broadcast[ByteString](2))
      val concat = b.add(Concat[ByteString](2))

      broadcast.out(0) ~> takeHead ~> concat.in(0)
      broadcast.out(1) ~> dropHead ~> concat.in(1)

      FlowShape(broadcast.in, concat.out)
    })
}
