import akka.NotUsed
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

  if (HttpProxy.hostsReplacement) HttpProxy.printHostsReplacementRules()

  connections runForeach { connection ⇒
    val outgoingConnection = Tcp().outgoingConnection(targetHost, targetPort)
    connection.handleWith {
      (if (HttpProxy.hostsReplacement)
         HttpProxy.replaceHosts
       else Flow[ByteString])
        .via(outgoingConnection)
    }
  }

  object HttpProxy {
    import collection.JavaConverters._
    private val hostsReplacementRules = config
      .getObject("http-proxy.hosts-replacement-rule")
      .unwrapped()
      .asScala
      .mapValues(_.toString)

    val hostsReplacement: Boolean =
      config.getBoolean("http-proxy.hosts-replacement") && hostsReplacementRules.nonEmpty

    def printHostsReplacementRules(): Unit = {
      hostsReplacementRules
        .foreach {
          case (key, value) =>
            println(s"[http-proxy hosts replacement rules] $key -> $value")
        }
    }

    val replaceHosts: Flow[ByteString, ByteString, NotUsed] = {
      val printLineIfNonEmpty = Flow[ByteString]
        .wireTap { line: ByteString =>
          if (line.nonEmpty) println(line.utf8String)
        }

      val requestLinePattern = s"([^ ]+) ([^ ]+) (.*)".r
      val replaceHostsIfNecessary = Flow[ByteString]
        .map(_.utf8String)
        .map {
          case requestLinePattern(method, originalRequestURI, httpVersion)
              if method == "CONNECT" =>
            val host :: remain = originalRequestURI.split(':').toList
            val requestURI = hostsReplacementRules
              .get(host)
              .map { replacedHost =>
                println(s"replaced: $host -> $replacedHost")
                (replacedHost :: remain).mkString(":")
              }
              .getOrElse(originalRequestURI)
            s"$method $requestURI $httpVersion"
          case line => line
        }
        .map(ByteString(_))

      import HttpRequestFlow.convertFirstLine
      convertFirstLine(replaceHostsIfNecessary.via(printLineIfNonEmpty))
    }
  }
}
