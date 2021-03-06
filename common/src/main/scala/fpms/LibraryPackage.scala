package fpms

import com.github.sh4869.semver_parser.SemVer
import io.circe.Encoder
import io.circe.Json
import io.circe.Decoder

case class LibraryPackage(
    name: String,
    version: SemVer,
    deps: Map[String, String],
    id: Int,
    shasum: String,
    integrity: Option[String]
)

object LibraryPackage {
  def apply(
      name: String,
      version: String,
      dep: Option[Map[String, String]],
      id: Int,
      shasum: String,
      integrity: Option[String]
  ) =
    new LibraryPackage(name, SemVer(version), dep.getOrElse(Map.empty[String, String]), id, shasum, integrity)

  def apply(
      name: String,
      version: String,
      dep: Map[String, String],
      id: Int,
      shasum: String,
      integrity: Option[String]
  ) =
    new LibraryPackage(name, SemVer(version), dep, id, shasum, integrity)

  implicit val encoder: Encoder[LibraryPackage] = new Encoder[LibraryPackage] {
    final def apply(a: LibraryPackage): Json =
      Json.obj(
        ("name", Json.fromString(a.name)),
        ("version", Json.fromString(a.version.original)),
        ("dep", Json.obj(a.deps.map(x => (x._1, Json.fromString(x._2))).toSeq: _*)),
        ("shasum", Json.fromString(a.shasum)),
        ("integrity", Json.fromString(a.integrity.getOrElse("")))
      )
  }

  implicit val decoder: Decoder[LibraryPackage] =
    Decoder.forProduct6[LibraryPackage, String, String, Map[String, String], Int, String, Option[String]](
      "name",
      "version",
      "deps",
      "id",
      "shasum",
      "integrity"
    )(
      LibraryPackage.apply
    )
}
