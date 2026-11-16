import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
object Siren {
  sealed trait SirenCommand
  case object StartSiren extends SirenCommand
  case object StopSiren extends SirenCommand

  def apply(): Behavior[SirenCommand] = deactivated()

  def deactivated(): Behavior[SirenCommand] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case StartSiren => {
        ctx.log.warn("Siren started")
        ctx.log.warn("NI NO NI NO!!!!")
        activated()
      }
      case StopSiren => Behaviors.same
    }
  }
  def activated(): Behavior[SirenCommand] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case StartSiren => Behaviors.same
      case StopSiren  => {
        ctx.log.warn("Siren stopped")
        deactivated()
      }
    }
  }
}
