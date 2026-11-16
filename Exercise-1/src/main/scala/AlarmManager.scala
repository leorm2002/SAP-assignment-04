import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import scala.annotation.switch
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import Sensor.Trigger
import AlarmSystem.AlarmSystemCommand
import AlarmSystem.ArmAreas
import AlarmSystem.Unarm
import AlarmSystem.Disconnected
import AlarmSystem.GetStateRespone
import AlarmSystem.GetState
import AlarmSystem.AlarmSystemResponse
import Keypad.KeypadCommand
object AlarmManager {
  sealed trait Command
  final case class GetComponents(replyTo: ActorRef[AlarmComponents]) extends Command
  final case class AlarmComponents(
      sensors: Map[String, EntityRef[Trigger.type]],
      system: EntityRef[AlarmSystemCommand],
      keypad: ActorRef[KeypadCommand]
  )

  def apply(config: Config): Behavior[Command] = Behaviors.setup {
    (ctx) =>
      ctx.log.info("System started")
      val sharding = ClusterSharding(ctx.system)
      sharding.init(Entity(AlarmSystem.TypeKey) { _ =>
        Behaviors
          .supervise(Behaviors.setup[AlarmSystemCommand] { controlCtx =>
            val siren = controlCtx.spawn(Siren(), "syren")
            AlarmSystem(config, siren)
          })
          .onFailure[Exception](SupervisorStrategy.restart)
      }.withRole(NodeRole.Control.configName))
      val system = sharding.entityRefFor(AlarmSystem.TypeKey, "home")

      // And finally the sensors
    sharding.init(Entity(Sensor.MotionTypeKey) { entity =>
      Sensor.motion(entity.entityId, system)
    }.withRole(NodeRole.Sensors.configName))
    sharding.init(Entity(Sensor.DoorWindowTypeKey) { entity =>
      Sensor.doorWindow(entity.entityId, system)
    }.withRole(NodeRole.Sensors.configName))
    val sensors: Map[String, EntityRef[Trigger.type]] = config.sensors.map { s =>
      val sensorRef: EntityRef[Trigger.type] = s.sensorType match {
        case SensorType.Motion => sharding.entityRefFor(Sensor.MotionTypeKey, s.name)
        case SensorType.DoorWindow => sharding.entityRefFor(Sensor.DoorWindowTypeKey, s.name)
      }
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
}
