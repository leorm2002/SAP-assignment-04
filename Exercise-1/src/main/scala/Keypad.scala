import org.apache.pekko.actor.typed.Behavior
import AlarmSystem.AlarmSystemCommand
import org.apache.pekko.actor.typed.RecipientRef
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import AlarmSystem.ArmAll
import AlarmSystem.ArmAreas
import AlarmSystem.Unarm
object Keypad {
  sealed trait KeypadCommand
  final case class ArmAreas(pin: String, areas: List[String]) extends KeypadCommand
  final case class ArmAll(pin: String) extends KeypadCommand
  final case class Unarm(pin: String) extends KeypadCommand

  def apply(system: RecipientRef[AlarmSystemCommand]): Behavior[KeypadCommand] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case ArmAreas(pin, areas) => {
        system ! AlarmSystem.ArmAreas(pin, areas)
        Behaviors.same
      }
      case ArmAll(pin) => {
        system ! AlarmSystem.ArmAll(pin)
        Behaviors.same
      }
      case Unarm(pin) => {
        system ! AlarmSystem.Unarm(pin)
        Behaviors.same
      }
    }
  }

}
