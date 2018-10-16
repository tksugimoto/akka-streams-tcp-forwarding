import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class HttpLineSeparatorSpec
    extends TestKit(ActorSystem())
    with WordSpecLike
    with BeforeAndAfterAll {

  final override def afterAll: Unit = shutdown()

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  import HttpLineSeparator.{dropFirstLine, takeFirstLine}

  "takeFirstLine" must {
    "1行目と2行目がまとまっていても1行目部分のみを取得する" in {
      val source = Source.single(
        ByteString(
          "CONNECT example.com:443 HTTP1.1\r\nHost: example.com:443\r\n\r\n"
        )
      )

      source
        .via(takeFirstLine)
        .runWith(TestSink.probe[ByteString])
        .request(1)
        .expectNext(ByteString("CONNECT example.com:443 HTTP1.1"))
        .expectComplete()
    }
    "1行目が分離してても1行目部分のみを取得する" in {
      val source = Source(
        List(
          ByteString("CONNECT example"),
          ByteString(".com:443 HTTP1.1\r\nHost: example.com:443\r\n\r\n")
        )
      )

      source
        .via(takeFirstLine)
        .runWith(TestSink.probe[ByteString])
        .request(1)
        .expectNext(ByteString("CONNECT example.com:443 HTTP1.1"))
        .expectComplete()
    }
  }
  "dropFirstLine" must {
    "1行目と2行目がまとまっていても1行目部分のみを切り捨てる" in {
      val source = Source.single(
        ByteString(
          "CONNECT example.com:443 HTTP1.1\r\nHost: example.com:443\r\n\r\n"
        )
      )

      source
        .via(dropFirstLine)
        .fold(ByteString.empty)(_ ++ _)
        .runWith(TestSink.probe[ByteString])
        .request(1)
        .expectNext(ByteString("\r\nHost: example.com:443\r\n\r\n"))
        .expectComplete()
    }
    "1行目が分離してても1行目部分のみを切り捨てる" in {
      val source = Source(
        List(
          ByteString("CONNECT example"),
          ByteString(".com:443 HTTP1.1\r\nHost: example.com:443\r\n\r\n")
        )
      )

      source
        .via(dropFirstLine)
        .fold(ByteString.empty)(_ ++ _)
        .runWith(TestSink.probe[ByteString])
        .request(1)
        .expectNext(ByteString("\r\nHost: example.com:443\r\n\r\n"))
        .expectComplete()
    }
  }
}
