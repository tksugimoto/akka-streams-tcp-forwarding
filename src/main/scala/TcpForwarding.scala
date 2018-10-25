import java.util.Date

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl.{Flow, Source, Tcp}
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
    println(
      s"[${new Date}] localAddress: ${connection.localAddress}, remoteAddress: ${connection.remoteAddress}")
    connection.handleWith(outgoingConnection)
  }

  def outgoingConnectionFactory(firstElement: ByteString) =
    firstElement.utf8String match {
      case firstLine =>
        println(s"[${new Date}] firstLine(${firstLine.length}): $firstLine")
        val outgoingConnection =
          Tcp().outgoingConnection(targetHost, targetPort)
        Future.successful(outgoingConnection)
    }
  val outgoingConnection: Flow[ByteString, ByteString, Any] = {
    import HttpRequestFlow.groupFirstLine
    groupFirstLine.via(Flow.lazyInit(outgoingConnectionFactory, () => ()))
  }
}
