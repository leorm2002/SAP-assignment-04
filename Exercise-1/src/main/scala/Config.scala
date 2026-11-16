import os.Path
import upickle.default._
import scala.annotation.publicInBinary

enum SensorType derives ReadWriter {
  @upickle.implicits.key("motion") case Motion
  @upickle.implicits.key("door-window") case DoorWindow
}

private case class SensorConfig(name: String, area: Option[String] = None, sensorType: SensorType) derives ReadWriter
private case class Config(
    useAreas: Boolean,
    entryDelay: Int,
    exitDelay: Int,
    sensors: List[SensorConfig],
    pin: String
) derives ReadWriter {
  def isValid(pinToCheck: String): Boolean = this.pin == pinToCheck

  def isSensorActive(sensorName: String, activeAreas: Option[List[String]]): Boolean = {
    activeAreas match {
      case Some(value) =>
        sensors
          .find(s => s.name.equals(sensorName))
          .map(conf => activeAreas.get.contains(conf.area.get))
          .getOrElse(false)
      case None => true
    }
  }

  def validateAreas(activeAreas: Option[List[String]]) = {
    // TODO: implementa
  }
}

object Config {
  private def fromJson(serialized: String): Config = {
    // TODO: catch errors and throws
    upickle.default.read[Config](serialized)
  }
  def read(path: Path): Config = {
    // TODO: if the file does not exists throw
    val content = os.read(path)
    val deserialized = fromJson(content)
    validateConfig(deserialized)
    deserialized
  }

  def validateConfig(config: Config) = {
    require(config.sensors.map(_.name).distinct.size == config.sensors.size, "Sensor names must be unique")
    if (config.useAreas && config.sensors.exists(s => s.area.isEmpty)) {
      var sensorWithMissing = config.sensors
        .filter(s => s.area.isEmpty)
        .map(s => s.name)
        .mkString(" ")
      throw new IllegalArgumentException(
        "Use areas enabled and some sensor have missing area (" + sensorWithMissing + ")"
      )
    }
  }
}
