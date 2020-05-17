package fpms

import io.circe.{Decoder, Encoder, Json}
case class RootInterface(
    name: String,
    versions: Seq[NpmPackageVersion]
)

case class NpmPackageVersion(
    version: String,
    dep: Option[Map[String, String]]
)

object NpmPackageVersion {
  implicit val encodeNpmPackageVersion: Encoder[NpmPackageVersion] =
    Encoder.forProduct2("version", "dep")(p => (p.version, p.dep))
  implicit val decodeNpmPackageVersion: Decoder[NpmPackageVersion] =
    Decoder
      .forProduct2[NpmPackageVersion, String, Option[Map[String, Json]]](
        "version",
        "dep"
      )((version, dep) =>
        NpmPackageVersion.apply(
          version,
          dep.map(_.filter { case (_, v) => v.isString }
            .map(x => (x._1, x._2.as[String].getOrElse(""))))
        )
      )
}
