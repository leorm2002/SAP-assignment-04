import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.ActorRef
import AlarmSystem.AlarmSystemCommand
import AlarmSystem.Tripped
object Sensor {
  sealed trait SensorCommand
  case object Trigger extends SensorCommand

  def apply(sensorName: String, system: ActorRef[AlarmSystemCommand]): Behavior[SensorCommand] = Behaviors.receive {
    (ctx, msg) =>
      msg match {
        case Trigger => {
          system ! Tripped(sensorName)
          Behaviors.same
        }
      }
  }

}
