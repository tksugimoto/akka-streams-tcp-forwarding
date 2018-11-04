import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString

object HttpLineSeparator {
  private val CR: Byte = ByteString("\r")(0)

  val takeFirstLine: Flow[ByteString, ByteString, NotUsed] = Flow[ByteString]
    .statefulMapConcat { () =>
      var inFirstLine = true
      input =>
        if (inFirstLine) {
          if (input.contains(CR)) {
            inFirstLine = false
            Vector(input.takeWhile(_ != CR))
          } else {
            Vector(input)
          }
        } else {
          Vector.empty
        }
    }
    .fold(ByteString.empty)(_ ++ _)
    .filterNot(_.isEmpty)

  val dropFirstLine: Flow[ByteString, ByteString, NotUsed] = Flow[ByteString]
    .statefulMapConcat { () =>
      var inFirstLine = true
      input =>
        if (inFirstLine) {
          if (input.contains(CR)) {
            inFirstLine = false
            Vector(input.dropWhile(_ != CR))
          } else {
            Vector.empty
          }
        } else {
          Vector(input)
        }
    }
}
