import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import scala.annotation.switch
import org.apache.pekko.actor.typed.ActorRef
import Sensor.SensorCommand
import org.apache.pekko.actor.typed.{ChildFailed, Terminated}
import Sensor.Trigger
import AlarmSystem.AlarmSystemCommand
import AlarmSystem.ArmAreas
import AlarmSystem.Unarm
import AlarmSystem.Disconnected
import AlarmSystem.GetStateRespone
import AlarmSystem.GetState
import AlarmSystem.AlarmSystemResponse
import Keypad.KeypadCommand
import org.apache.pekko.cluster.typed.Cluster
object AlarmManager {
  sealed trait Command
  final case class GetComponents(replyTo: ActorRef[AlarmComponents]) extends Command
  final case class AlarmComponents(
      sensors: Map[String, ActorRef[SensorCommand]],
      system: ActorRef[AlarmSystemCommand],
      keypad: ActorRef[KeypadCommand]
  )

  def apply(config: Config): Behavior[Command] = Behaviors.setup {
    (ctx) =>
      val cluster = Cluster(ctx.system)
      ctx.log.info(s"Pekko Node ${cluster.selfMember.address} is initializing...")

      ctx.log.info("System started")
      // Istantiating the siran
      val siren = ctx.spawn(Siren(), "syren")
      // The alarm center
      val system = ctx.spawn(AlarmSystem(config, siren), "alarm")

      // And finally the sensors
    val sensors = config.sensors.map { s =>
      // Spawni the sensor
      val sensorRef = ctx.spawn(Sensor(s.name, system), s.name)
      // Watch the sensor lyce cycle
      ctx.watch(sensorRef)
      (s.name, sensorRef)
    }.toMap

    val keypad = ctx.spawn(Keypad(system), "keypad")
    started(config, AlarmComponents(sensors, system, keypad))
  }
  def started(
      config: Config,
      components: AlarmComponents
  ): Behavior[Command] = Behaviors
    .receive[Command] { (ctx, msg) =>
      msg match {
        case GetComponents(replyTo) =>
          replyTo ! components
          Behaviors.same
      }
    }
    .receiveSignal {
      case (ctx, ChildFailed(ref, cause)) => {
        ctx.log.error(s"The sensor ${ref.path.name} died: ${cause.getMessage}")
        // Logica allarme...
        components.system ! Disconnected(ref.path.name)
        Behaviors.same
      }
    }
}
