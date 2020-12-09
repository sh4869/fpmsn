package fpms.calcurator.rds

import cats.effect.ConcurrentEffect
import cats.effect.concurrent.MVar
import cats.effect.concurrent.MVar2
import cats.Parallel
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging

import fpms.calcurator.ldil.LDILMap
import cats.effect.ConcurrentEffect
import cats.effect.concurrent.Semaphore
import cats.effect.Concurrent

class RDSMapCalcuratorOnMemory[F[_]](implicit F: ConcurrentEffect[F], P: Parallel[F])
    extends RDSMapCalcurator[F]
    with LazyLogging {

  def calc(ldilMap: LDILMap): F[RDSMap[F]] = {
    val initedMap = initMap(ldilMap)
    val allMap = initedMap._1
    val allMapList = allMap.toList
    var updated = initedMap._2
    // Loop
    val semaphor = F.toIO(Semaphore.apply(32)).unsafeRunSync()
    while (updated.nonEmpty) {
      logger.info(s"updated size: ${updated.size}")
      val updateInLoop = F.toIO(MVar.of[F, Set[Int]](Set.empty[Int])).unsafeRunSync()
      val lock = F.toIO(MVar.of[F, Unit](())).unsafeRunSync()
      F.toIO(lock.take).unsafeRunSync()
      val count = F.toIO(MVar.of[F, Int](0)).unsafeRunSync()
      val checkFunction: (Int => Boolean) =
        if (updated.size / ldilMap.size > 0.5) { (_) => true }
        else updated.contains
      // 最初からSemaphoreでやってくれるようなやつを作る必要がある
      F.toIO(
          Concurrent
            .parSequenceN(16)(allMapList.map {
              case (id, setMvar) =>
                for {
                  _ <- semaphor.acquire
                  set <- setMvar.read
                  newSet <- MVar.of[F, Set[Int]](set)
                  _ <- {
                    ldilMap.get(id).fold(F.pure(())) { value =>
                      value.map { tid =>
                        if (checkFunction(tid)) {
                          for {
                            x <- allMap.get(tid).get.read
                            z <- newSet.take
                            _ <- newSet.put(x ++ z)
                          } yield ()
                        } else F.unit
                      }.parSequence.void
                    }
                  }
                  x <- newSet.read
                  _ <- if (x.size > set.size) {
                    for {
                      _ <- updateInLoop.take.flatMap(list => updateInLoop.put(list + id))
                      _ <- setMvar.swap(x)
                    } yield ()
                  } else F.pure(())
                  x <- count.take
                  _ <- if (x + 1 >= allMap.size) lock.put(()) else F.pure(())
                  _ <- F.pure(if (x % 1000000 == 0) logger.info(s"$x"))
                  _ <- count.put(x + 1)
                  _ <- semaphor.release
                } yield ()
            })
            .void
        )
        .unsafeRunAsyncAndForget()
      // lockから取れるようになるまで待つ
      F.toIO(lock.take).unsafeRunSync()
      updated = F.toIO(updateInLoop.read).unsafeRunSync()
    }
    F.pure(allMap)
  }

  private def initMap(ldilMap: LDILMap): (Map[Int, MVar2[F, Set[Int]]], Set[Int]) = {
    val allMap = scala.collection.mutable.Map.empty[Int, MVar2[F, Set[Int]]]
    val updatedIni = scala.collection.mutable.TreeSet.empty[Int]
    ldilMap.toList.map {
      case (id, set) => {
        if (set.nonEmpty) {
          allMap.update(id, F.toIO(MVar.of(Set(set: _*))).unsafeRunSync())
          updatedIni += id
        }
      }
    }
    (allMap.toMap, updatedIni.toSet)
  }
}
