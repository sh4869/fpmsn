package fpms.calculator.ldil

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import cats.Parallel
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging

import fpms.LibraryPackage
import fpms.LDIL.LDILMap
import fpms.calculator.json.JsonLoader

class LDILMapCalculatorOnMemory[F[_]](implicit F: ConcurrentEffect[F], P: Parallel[F], cs: ContextShift[F])
    extends LDILMapCalculator[F]
    with LazyLogging {

  private val added = scala.collection.mutable.ListBuffer.empty[LibraryPackage]
  def init: F[LDILMap] = {
    updateMap(JsonLoader.createNamePackagesMap())
  }

  def update(adds: Seq[LibraryPackage]): F[LDILMap] = {
    added ++= adds
    // 追加されたパッケージについてpackMapを更新する
    // (packMapに存在していれば（すでに存在するパッケージの新しいバージョンであれば）最後に追加して更新、そうでなければ新しいキーの作成)
    val packMap = scala.collection.mutable.Map.empty[String, Seq[LibraryPackage]] ++ JsonLoader.createNamePackagesMap()
    added.foreach { v => packMap.update(v.name, packMap.get(v.name).getOrElse(Seq.empty) :+ v) }
    updateMap(packMap.toMap)
  }

  private def updateMap(packMap: Map[String, Seq[LibraryPackage]]): F[LDILMap] = {
    val packsGroupedByName = packMap.values.toList.map(_.toList).grouped(packMap.size / 15)
    val finder = new LatestDependencyFinder(packMap.get)
    logger.info(s"number of names of packages : ${packMap.size}")
    val executor = Executors.newFixedThreadPool(16)
    val context = ExecutionContext.fromExecutor(executor)
    val func = (list: List[List[LibraryPackage]]) => {
      val map = scala.collection.mutable.Map.empty[Int, List[Int]]
      list.foreach(_.foreach { pack =>
        try {
          val ids = finder.findIds(pack)
          map.update(pack.id, ids)
        } catch {
          case e: InterruptedException => logger.debug(e.getMessage())
          case _: Throwable            => ()
        }
      })
      map.toMap
    }
    val z: F[LDILMap] = packsGroupedByName
      .map(list => F.async[Map[Int, List[Int]]](_(Right(func(list)))))
      .toList
      .parSequence
      .map(_.flatten.toMap)
    logger.info("start calculate ldil")
    cs.evalOn(context)(z)
  }
}
