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
    val outgoingConnection = HttpProxy.SwitchByRequestTarget.outgoingConnection
    connection.handleWith(outgoingConnection)
  }

  HttpProxy.SwitchByRequestTarget.printSwitchRules()

  object HttpProxy {
    object SwitchByRequestTarget {
      case class ProxyTarget(host: String, port: Int)
      object ProxyTarget {
        private val hostPortPattern = """([^:]+):(\d+)""".r
        def apply(hostPortString: String): ProxyTarget = {
          hostPortString match {
            case hostPortPattern(host, port) => ProxyTarget(host, port.toInt)
            case _ =>
              throw new IllegalArgumentException(
                s"switch-rule.conf の値が <host>:<port> 形式ではありません: $hostPortString")
          }
        }
        def apply(hostPortString: Any): ProxyTarget =
          ProxyTarget(hostPortString.toString)
      }

      private val defaultProxyTarget = ProxyTarget(targetHost, targetPort)

      import collection.JavaConverters._
      private val switchRules =
        config
          .getObject("http-proxy.switch-by-request-target.switch-rule")
          .unwrapped()
          .asScala
          .mapValues(ProxyTarget(_))

      def printSwitchRules(): Unit = {
        switchRules
          .foreach {
            case (key, value) =>
              println(s"[http-proxy switch rules] $key -> $value")
          }
      }

      private val requestLinePattern = "[^ ]+ (?:http://)?([^/:]+).*".r

      private def outgoingConnectionFactory(firstElement: ByteString): Future[
        Flow[ByteString, ByteString, Future[Tcp.OutgoingConnection]]] = {
        val ProxyTarget(host, port) = firstElement.utf8String match {
          case requestLinePattern(hostname) =>
            switchRules.get(hostname) match {
              case Some(proxyTarget) =>
                println(
                  s"switched -> $proxyTarget (${firstElement.utf8String})")
                proxyTarget
              case None => defaultProxyTarget
            }
          case _ =>
            defaultProxyTarget
        }
        Future.successful(Tcp().outgoingConnection(host, port))
      }

      val outgoingConnection: Flow[ByteString, ByteString, Any] = {
        import HttpRequestFlow.groupFirstLine
        groupFirstLine.via(Flow.lazyInit(outgoingConnectionFactory, () => ()))
      }
    }
  }
}
