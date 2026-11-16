import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration._
import org.apache.pekko.actor.typed.scaladsl.TimerScheduler
import org.apache.pekko.actor.typed.ActorRef
import Siren.SirenCommand
import Siren.StartSiren

object AlarmSystem {
  sealed trait AlarmSystemCommand

  // Public
  final case class ArmAll(pin: String) extends AlarmSystemCommand
  final case class ArmAreas(pin: String, areas: List[String]) extends AlarmSystemCommand
  final case class Unarm(pin: String) extends AlarmSystemCommand
  final case class Tripped(sensorName: String) extends AlarmSystemCommand
  final case class Disconnected(sensorName: String) extends AlarmSystemCommand
  final case class GetState(replyTo: ActorRef[AlarmSystemResponse]) extends AlarmSystemCommand

  // Internal transitions
  private case object ArmAllNow extends AlarmSystemCommand
  private final case class ArmAreasNow(areas: List[String]) extends AlarmSystemCommand
  private final case class AlarmNow(sensorName: String) extends AlarmSystemCommand

  sealed trait AlarmSystemResponse
  final case class GetStateRespone(alarmState: AlarmState) extends AlarmSystemResponse

  // Timer keys
  private case object ArmTimerKey
  private case object AlarmTimerKey

  enum AlarmState {
    case Unarmed
    case Arming
    case Armed
    case Prealarming
    case Alarm
    case SensorDied
  }

  def apply(config: Config, siren: ActorRef[SirenCommand]): Behavior[AlarmSystemCommand] =
    Behaviors.withTimers(timers => unarmed(config, timers, siren))

  def unarmed(
      config: Config,
      timers: TimerScheduler[AlarmSystem.AlarmSystemCommand],
      siren: ActorRef[SirenCommand]
  ): Behavior[AlarmSystemCommand] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case ArmAll(pin) if config.isValid(pin) => {
          if (!timers.isTimerActive(ArmTimerKey)) {
            ctx.log.info("Arming the system")
            timers.startSingleTimer(ArmTimerKey, ArmAllNow, config.exitDelay.seconds)
          } else {
            ctx.log.info("Arming already started, won't be ovverriden")
          }
          Behaviors.same
        }
        case ArmAll(_) => {
          ctx.log.info("Tried to arm the system but used the wrong pin")
          Behaviors.same
        }
        case ArmAreas(pin, areas) if config.isValid(pin) => {
          if (!timers.isTimerActive(ArmTimerKey)) {
            ctx.log.info("Arming the system")
            timers.startSingleTimer(ArmTimerKey, ArmAreasNow(areas), config.exitDelay.seconds)
          } else {
            ctx.log.info("Arming already started, won't be ovverriden")
          }
          Behaviors.same
        }
        case ArmAreas(_, _) => {
          ctx.log.info("Tried to arm the system but used the wrong pin")
          Behaviors.same
        }
        // This cancel the arm called before, if any is present
        case Unarm(pin) if config.isValid(pin) => {
          if (!timers.isTimerActive(ArmTimerKey)) {
            ctx.log.info("Tried to unarm unarmed system with no arming present, nothing happen")
          } else {
            ctx.log.info("Tried to unarm unarmed system during arming, canceled")
          }
          timers.cancel(ArmTimerKey)
          Behaviors.same
        }
        case Unarm(_) => {
          ctx.log.info("Tried to unarm unarmed system with wrong pin")
          Behaviors.same
        }
        case Tripped(sensorName) => {
          if (!timers.isTimerActive(ArmTimerKey)) {
            ctx.log.info(s"Sensor ${sensorName} tripped, but the state is UNARMED")
          } else {
            ctx.log.info(s"Sensor ${sensorName} tripped, but the state is  ARMING")
          }

          Behaviors.same
        }
        case ArmAllNow => {
          ctx.log.info("System armed NOW")
          armed(config, Option.empty, timers, siren)
        }
        case ArmAreasNow(areas) => {
          ctx.log.info("System armed NOW")
          armed(config, Some(areas), timers, siren)
        }
        case AlarmNow(sensorName)     => Behaviors.same
        case Disconnected(sensorName) => panick(config, timers, siren)
        case GetState(replyTo)        => {
          if (timers.isTimerActive(ArmTimerKey)) {
            replyTo ! GetStateRespone(AlarmState.Arming)
          } else {
            replyTo ! GetStateRespone(AlarmState.Unarmed)
          }
          Behaviors.same

        }

      }
    }

  def armed(
      config: Config,
      areas: Option[List[String]],
      timers: TimerScheduler[AlarmSystem.AlarmSystemCommand],
      siren: ActorRef[SirenCommand]
  ): Behavior[AlarmSystemCommand] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Unarm(pin) if config.isValid(pin) => {
          ctx.log.info("System unarmed")
          timers.cancel(AlarmTimerKey)
          unarmed(config, timers, siren)
        }
        case Unarm(_) => {
          ctx.log.info("System not unarmed (wrong pin)")
          Behaviors.same
        }
        case Tripped(sensorName) if config.isSensorActive(sensorName, areas) => {

          if (!timers.isTimerActive(AlarmTimerKey)) {
            ctx.log.info(s"Sensor ${sensorName} tripped starting the timer")
            timers.startSingleTimer(AlarmTimerKey, AlarmNow(sensorName), config.entryDelay.seconds)
          } else {
            ctx.log.info(s"Sensor ${sensorName} tripped but another already been, no restarting the timer")
          }
          Behaviors.same
        }
        case Tripped(sensorName) => {
          ctx.log.info(s"Sensor ${sensorName} tripped but it's area it's not armed")
          Behaviors.same
        }
        case AlarmNow(sensorName) => {
          ctx.log.info(s"Alarm going on now")
          siren ! Siren.StartSiren
          alarm(config, timers, siren)
        }
        // Those should not happen
        case ArmAllNow                => Behaviors.same
        case ArmAreasNow(areas)       => Behaviors.same
        case ArmAll(pin)              => Behaviors.same
        case ArmAreas(pin, areas)     => Behaviors.same
        case Disconnected(sensorName) => panick(config, timers, siren)
        case GetState(replyTo)        => {
          if (timers.isTimerActive(ArmTimerKey)) {
            replyTo ! GetStateRespone(AlarmState.Prealarming)
          } else {
            replyTo ! GetStateRespone(AlarmState.Armed)
          }
          Behaviors.same

        }
      }
    }

  def alarm(
      config: Config,
      timers: TimerScheduler[AlarmSystem.AlarmSystemCommand],
      siren: ActorRef[SirenCommand]
  ): Behavior[AlarmSystemCommand] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Unarm(pin) if config.isValid(pin) => {
          siren ! Siren.StopSiren
          unarmed(config, timers, siren)
        }
        case Unarm(_) => {
          ctx.log.info("Tried to unarm but recieved the wrong pin")
          Behaviors.same
        }
        // Do nothing here
        case ArmAll(pin)              => Behaviors.same
        case ArmAreas(pin, areas)     => Behaviors.same
        case Tripped(sensorName)      => Behaviors.same
        case ArmAllNow                => Behaviors.same
        case ArmAreasNow(areas)       => Behaviors.same
        case AlarmNow(sensorName)     => Behaviors.same
        case Disconnected(sensorName) => panick(config, timers, siren)
        case GetState(replyTo)        => {
          replyTo ! GetStateRespone(AlarmState.Alarm)
          Behaviors.same
        }
      }
    }

  def panick(
      config: Config,
      timers: TimerScheduler[AlarmSystem.AlarmSystemCommand],
      siren: ActorRef[SirenCommand]
  ): Behavior[AlarmSystemCommand] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Unarm(pin) if config.isValid(pin) => {
          siren ! Siren.StopSiren
          unarmed(config, timers, siren)
        }
        case Unarm(_) => {
          ctx.log.info("Tried to stop the sensor panick but recieved the wrong pin")
          Behaviors.same
        }
        // Do nothing here
        case ArmAll(pin)              => Behaviors.same
        case ArmAreas(pin, areas)     => Behaviors.same
        case Tripped(sensorName)      => Behaviors.same
        case ArmAllNow                => Behaviors.same
        case ArmAreasNow(areas)       => Behaviors.same
        case AlarmNow(sensorName)     => Behaviors.same
        case Disconnected(sensorName) => ???
        case GetState(replyTo)        => {
          replyTo ! GetStateRespone(AlarmState.SensorDied)
          Behaviors.same
        }
      }
    }

}
