package fpms.calculator.json

import java.io.PrintWriter
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.apache.commons.io.FileUtils

import fpms.LibraryPackage

object JsonLoader extends LazyLogging {

  private lazy val config = ConfigFactory.load("app.conf").getConfig("json")
  lazy val MAX_FILE_COUNT = config.getInt("filenum")

  def loadList(start: Int = 0, end: Int = MAX_FILE_COUNT): List[RootInterface] = {
    var lists = Seq.empty[Option[List[RootInterface]]]
    for (i <- start to end) {
      logger.info(s"json file: ${i}/${end}")
      val src = readFile(filepath(i))
      val dec = decode[List[RootInterface]](src) match {
        case Right(v) => Some(v)
        case Left(_)  => None
      }
      lists = lists :+ dec.map(x => x.map(v => v.copy(name = URLDecoder.decode(v.name, StandardCharsets.UTF_8.name))))
    }
    lists.flatten.flatten[RootInterface].toList
  }

  def loadIdList(start: Int = 0, end: Int = MAX_FILE_COUNT): List[RootInterfaceN] = {
    logger.info(s"load ${end} json files")
    val lists = scala.collection.mutable.ListBuffer.empty[RootInterfaceN]
    for (i <- start to end) {
      val src = readFile(s"${config.getString("idjsondir")}$i.json")
      val dec = decode[List[RootInterfaceN]](src) match {
        case Right(v) => Some(v)
        case Left(e) => {
          logger.error(s"failed to load file of $i.json", e)
          None
        }
      }
      lists ++= dec
        .map(x => x.map(v => v.copy(name = URLDecoder.decode(v.name, StandardCharsets.UTF_8.name))))
        .getOrElse(Seq.empty)
    }
    lists.toList
  }

  def convertJson(start: Int = 0, end: Int = MAX_FILE_COUNT) = {
    logger.info("convert to json")
    var id = 0;
    for (i <- start to end) {
      val src = readFile(filepath(i))
      decode[List[RootInterface]](src) match {
        case Right(v) => {
          val x = convertList(v, id)
          id = x._2
          new PrintWriter(s"${config.getString("idjsondir")}${i}.json") {
            write(x._1.asJson.noSpaces)
            close()
          }
          logger.info(s"writing out $i.json")
        }
        case Left(e) => logger.error(s"error on $i.json convert json file:", e)
      }
    }
  }

  def createNamePackagesMap(): Map[String, Seq[LibraryPackage]] = {
    val packs = loadIdList()
    logger.info("loaded json files")
    val packs_map = scala.collection.mutable.Map.empty[String, Seq[LibraryPackage]]
    packs.foreach { pack =>
      val seq = scala.collection.mutable.ListBuffer.empty[LibraryPackage]
      pack.versions.foreach { d =>
        try {
          val info = LibraryPackage(pack.name, d.version, d.dep, d.id, d.shasum, d.integrity)
          seq += info
        } catch {
          case _: Throwable => {
            logger.info(s"error parsing version: ${pack.name}, ${d.version}")
          }
        }
      }
      packs_map += (pack.name -> seq.toSeq)
    }
    logger.info("complete convert to list")
    packs_map.toMap
  }

  private def convertList(array: List[RootInterface], start: Int): (List[RootInterfaceN], Int) = {
    var id = start
    val result = array.map(y =>
      RootInterfaceN(y.name, y.versions.map(v => {
        val x = NpmPackageWithId(v.version, v.dep, id, v.shasum, v.integrity)
        id += 1
        x
      }))
    )
    (result, id)
  }

  private def filepath(count: Int): String =
    s"${config.getString("jsondir")}$count.json"

  private def readFile(filename: String): String = FileUtils.readFileToString(new java.io.File(filename), "utf-8")
}
