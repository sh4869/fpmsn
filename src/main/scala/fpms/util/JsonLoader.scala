package fpms.util

import fpms.RootInterface
import io.circe.generic.auto._
import io.circe.parser.decode
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
import scala.io.Source
import java.net.URLDecoder

object JsonLoader {

  val MAX_FILE_COUNT = 63
  private val logger = LoggerFactory.getLogger(this.getClass)

  def createLists(count: Int = MAX_FILE_COUNT): Seq[RootInterface] = {
    var lists = Seq.empty[Option[List[RootInterface]]]
    val charset = StandardCharsets.UTF_8.name
    for (i <- 0 to count) {
      val src = readFile(filepath(i))
      val dec = decode[List[RootInterface]](src) match {
        case Right(v) => Some(v)
        case Left(e) => {
          logger.warn(s"error: $i, ${e.toString()}")
          None
        }
      }
      lists = lists :+ dec.map(x => x.map(v => v.copy(name = URLDecoder.decode(v.name, charset))))
    }
    lists.flatten.flatten[RootInterface]
  }

  private def filepath(count: Int): String =
    s"/home/al16030/Repos/package-manager-server/jsons/$count.json"

  private def readFile(filename: String): String = {
    val source = Source.fromFile(filename)
    val result = source.getLines.mkString
    source.close()
    result
  }

  private def parse(src: String): Option[List[RootInterface]] = {
    val deco = decode[List[RootInterface]](src)
    deco match {
      case Right(v) => Some(v)
      case Left(e) => {
        logger.warn(s"${e.toString()}")
        None
      }
    }
  }
}
