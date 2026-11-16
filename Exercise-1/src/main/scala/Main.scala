import os.Path
import org.apache.pekko.actor.typed.ActorSystem
import scala.concurrent.duration._
import scala.concurrent.Future
import Sensor.Trigger
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.Scheduler
import scala.concurrent.Await
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory

@main def run(port: String): Unit = {
  val config = Config.read(os.pwd / "config.json")

  // Automatically load the factory and override the port
  val baseConfig = ConfigFactory.load()
  val clusterConfig = baseConfig.withValue(
    "pekko.remote.artery.canonical.port",
    ConfigValueFactory.fromAnyRef(port.toInt)
  )

  // We now have our validated config we can start our actor system
  val systemRef = ActorSystem(AlarmManager(config), "Alarm", clusterConfig)

  // Setting up all the infos to make tha ? sytnax working
  implicit val ec: ExecutionContext = systemRef.executionContext
  implicit val timeout: org.apache.pekko.util.Timeout = 3.seconds
  given Scheduler = systemRef.scheduler

  // Extract the state, we're breaking the boundaries of the actor system and itereacting directly with the actors.
  // This is a workaround to simulate the actual interaction with the physical system wich should underlyies the actors
  val futureComponents: Future[AlarmManager.AlarmComponents] = systemRef ? (ref => AlarmManager.GetComponents(ref))

  // 3. Ora hai l'oggetto fuori dall'attore e puoi usarlo
  val execution = futureComponents.map {
    system =>
      // First trigger, nothing should happen
      system.sensors.get("SENSOR_1").get ! Trigger
    waitABIt()
    // Now arm area1 with wrong pin
    system.keypad ! Keypad.ArmAreas("1111", List("AREA_1"))
    waitABIt()
    // Noew arm are1 with correct pin
    system.keypad ! Keypad.ArmAreas("1234", List("AREA_1"))
    waitABIt()
    // Now trigger sensor 1 nothing happen , exit delay
    system.sensors.get("SENSOR_1").get ! Trigger
    // Wait 6 seconds
    Thread.sleep(6000)
    // trigger SENSOR_4 (nothinh happend, it's area 2)
    system.sensors.get("SENSOR_4").get ! Trigger
    waitABIt()

    // Now trigger sensor 1
    system.sensors.get("SENSOR_1").get ! Trigger
    waitABIt()

    // Unarm the system after 3 second (the delay it's 5) so no alarm
    Thread.sleep(3000)
    system.keypad ! Keypad.Unarm("1234")
    // wait another 3 seconds to verify (the unarm should be istant)
    Thread.sleep(3000)
    // now arm again
    system.keypad ! Keypad.ArmAreas("1234", List("AREA_1"))
    waitABIt()
    // wait 6 seconds, make it become armed
    Thread.sleep(6000)
    // trip the sensor
    system.sensors.get("SENSOR_1").get ! Trigger
    // wait 6 seconds, make it start the alarm
    Thread.sleep(6000)
    // now deactivate with the pin
    system.keypad ! Keypad.Unarm("1234")
  }

  // We wait for the demo to end
  Await.result(execution, 60.seconds)

  println("Press ENTER to terminate")
  scala.io.StdIn.readLine()
  systemRef.terminate()
}

def waitABIt(): Unit =
  Thread.sleep(100)
