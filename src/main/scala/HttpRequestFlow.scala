import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL}
import akka.util.ByteString

object HttpRequestFlow {

  /**
    * HTTPリクエストの1行目を書き換えるflowを生成する
    * @param f HTTPリクエストの1行目の変換処理
    *          （末尾のCRLFを除いた1行目が1要素で流れる）
    * @return
    */
  def convertFirstLine(f: Flow[ByteString, ByteString, NotUsed])
    : Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      import HttpLineSeparator._

      val broadcast = builder.add(Broadcast[ByteString](2))
      val concat = builder.add(Concat[ByteString](2))

      broadcast.out(0) ~>
        takeFirstLine ~> f ~>
        concat.in(0)

      broadcast.out(1) ~>
        dropFirstLine ~>
        concat.in(1)

      FlowShape(broadcast.in, concat.out)
    })
}
