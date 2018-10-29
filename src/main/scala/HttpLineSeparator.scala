import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString

object HttpLineSeparator {
  private val CR = ByteString("\r")

  // TODO: 効率化　(1Byteずつにsplitして判定するのは非効率)
  private val splitByteString = Flow[ByteString].mapConcat(_.map(ByteString(_)))

  val takeFirstLine: Flow[ByteString, ByteString, NotUsed] = Flow[ByteString]
    .via(splitByteString)
    .takeWhile(_ != CR)
    .fold(ByteString.empty)(_ ++ _)
    .filterNot(_.isEmpty)

  val dropFirstLine: Flow[ByteString, ByteString, NotUsed] = Flow[ByteString]
    .via(splitByteString)
    .dropWhile(_ != CR)
}
