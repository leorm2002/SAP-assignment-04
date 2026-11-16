import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.scaladsl.adapter._
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.ClassicActorSystemProvider
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.util.Timeout
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.{Source, StdIn}
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.RecipientRef
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import Keypad.KeypadCommand
import AlarmSystem.AlarmSystemCommand
import spray.json.DefaultJsonProtocol._
import spray.json._

object HttpServer {

  def main(args: Array[String]): Unit = {
    require(args.nonEmpty, "Usage: HttpServer <cluster-port>")

    val config: Config = Config.read(os.pwd / "config.json")
    val system: ActorSystem[AlarmManager.Command] =
      Main.start(config, args(0).toInt, NodeRole.Keypad)

    // Setting up all the implicits for pekko to work
    implicit val executionContext: ExecutionContext = system.executionContext
    implicit val timeout: Timeout = 3.seconds
    given Scheduler = system.scheduler
    given ClassicActorSystemProvider = system.toClassic

    // Extract the components
    val components: AlarmManager.AlarmComponents =
      Await.result(system ? (replyTo => AlarmManager.GetComponents(replyTo)), timeout.duration)

    // Load the html dashboard from resources
    val dashboard = loadHtmlFile(resourceAsString("dashboard.html"))

    // To extract system status
    def status(): Future[HttpResponse] = {
      val state = components.system ? (replyTo => AlarmSystem.GetState(replyTo))
      state.map { case AlarmSystem.GetStateRespone(alarmState) => ok(alarmState.toString) }
    }

    // All the exposed endpoints
    val route: Route =
      concat(
        pathEndOrSingleSlash {
          get { complete(dashboard) }
        },
        path("sensors") {
          get { complete(jsonResponse(components.sensors.keys.toList.sorted.toJson.compactPrint)) }
        },
        path("trigger" / Segment) { (sensorName: String) =>
          post { complete(trigger(sensorName, components.sensors)) }
        },
        path("arm" / Segment) { (pin: String) =>
          post { complete(arm(pin, components.keypad)) }
        },
        path("unarm" / Segment) { (pin: String) =>
          post { complete(unarm(pin, components.keypad)) }
        },
        path("getStatus" | "status") {
          get { complete(status()) }
        }
      )

    val bindingFuture: Future[Http.ServerBinding] =
      Http()(using system).newServerAt("localhost", 8080).bind(Route.toFunction(route))

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine()

    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}

def textResponse(statusCode: StatusCode, body: String): HttpResponse = {
  HttpResponse(
    status = statusCode,
    entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, body)
  )
}

def loadHtmlFile(body: String): HttpResponse = {
  HttpResponse(
    status = StatusCodes.OK,
    entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, body)
  )
}

def ok(body: String): HttpResponse = textResponse(StatusCodes.OK, body)

def jsonResponse(body: String): HttpResponse = {
  HttpResponse(
    status = StatusCodes.OK,
    entity = HttpEntity(ContentTypes.`application/json`, body)
  )
}

def resourceAsString(resourceName: String): String = {
  val source = Source.fromResource(resourceName)
  try {
    source.mkString
  } finally {
    source.close()
  }
}
def trigger(sensorName: String, sensors: Map[String, RecipientRef[Sensor.Trigger.type]]): HttpResponse = {
  sensors(sensorName) ! Sensor.Trigger
  ok(s"triggered:$sensorName")
}

def arm(pin: String, keypad: ActorRef[KeypadCommand]): HttpResponse = {
  keypad ! Keypad.ArmAll(pin)
  ok("arming")
}

def unarm(pin: String, keypad: ActorRef[KeypadCommand]): HttpResponse = {
  keypad ! Keypad.Unarm(pin)
  ok("unarming")
}
