package fpms.util

import fpms._
import fpms.json.RootInterfaceN
import fpms.repository.db.SourcePackageSqlRepository
import cats.effect.IO
import org.slf4j.LoggerFactory
import scala.util.Try

object SqlSaver {
  private lazy val logger = LoggerFactory.getLogger(this.getClass)
  def saveJson(packages: Array[RootInterfaceN], repo: SourcePackageSqlRepository[IO]) = {
    for (i <- 0 to packages.length - 1) {
      if (i % 10000 == 0) logger.info(s"save in db: ${i + 1}/${packages.length - 1}")
      val pack = packages(i)
      val list =
        pack.versions.map(x => Try { Some(LibraryPackage(pack.name, x.version, x.dep, x.id)) }.getOrElse(None)).flatten
      repo.insert(list.toList).unsafeRunSync()
    }
  }
}
