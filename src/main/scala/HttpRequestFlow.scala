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

      broadcast
        .out(0) ~>
        takeFirstLine ~>
        util.LogFlow.log("after takeFirstLine") ~>
        f ~>
        concat.in(0)

      broadcast.out(1) ~>
        dropFirstLine ~>
        util.LogFlow.log("after dropFirstLine") ~>
        concat.in(1)

      FlowShape(broadcast.in, concat.out)
    })

  /**
    * HTTPリクエストの1行目（CRLFを除く）のみをstreamsの1要素目に流すflow
    * （1行目のCRLFと2行目以降は2要素目以降に流れる）
    * @return
    */
  def groupFirstLine: Flow[ByteString, ByteString, NotUsed] =
    convertFirstLine(Flow[ByteString])
}
