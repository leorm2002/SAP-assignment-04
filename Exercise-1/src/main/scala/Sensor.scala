import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.RecipientRef
import org.apache.pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import AlarmSystem.AlarmSystemCommand
import AlarmSystem.Tripped
object Sensor {
  sealed trait Command extends CborSerializable
  case object Trigger extends Command
  case object MotionDetected extends Command
  case object DoorOpened extends Command
  case object WindowOpened extends Command

  type SensorCommand[T <: SensorType] <: Command = T match {
    case SensorType.Motion.type     => Trigger.type | MotionDetected.type
    case SensorType.DoorWindow.type => Trigger.type | DoorOpened.type | WindowOpened.type
  }

  val MotionTypeKey: EntityTypeKey[SensorCommand[SensorType.Motion.type]] = EntityTypeKey("MotionSensor")
  val DoorWindowTypeKey: EntityTypeKey[SensorCommand[SensorType.DoorWindow.type]] = EntityTypeKey("DoorWindowSensor")

  def motion(sensorId: String, system: RecipientRef[AlarmSystemCommand]): Behavior[SensorCommand[SensorType.Motion.type]] =
    Behaviors.receiveMessage { case Trigger | MotionDetected =>
      system ! Tripped(sensorId)
      Behaviors.same
    }

  def doorWindow(sensorId: String, system: RecipientRef[AlarmSystemCommand]): Behavior[SensorCommand[SensorType.DoorWindow.type]] =
    Behaviors.receiveMessage { case Trigger | DoorOpened | WindowOpened =>
      system ! Tripped(sensorId)
      Behaviors.same
    }
}
