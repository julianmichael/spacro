package spacro.tasks

import spacro._
import spacro.util._

import java.util.Date

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorSystem

import akka.stream.stage._
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl._

import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{ Message, TextMessage, BinaryMessage }

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

import upickle.default._

import com.typesafe.scalalogging.StrictLogging

/** Implements the logic of the web server that hosts the given MTurk tasks.
  * Each TaskSpecification has its own Flow that specifies how to respond to WebSocket messages from clients.
  * This web service hosts the JS code that clients GET, and delegates Websocket messages to their tasks' flows.
  *
  * It also hosts a sample version of each task at http(s)://<domain>:<port>/task/<task key>/preview.
  */
class Webservice(
  tasks: List[TaskSpecification])(
  implicit fm: Materializer,
  config: TaskConfig) extends Directives with StrictLogging {

  // assume keys are unique
  val taskIndex = tasks.map(t => (t.taskKey -> t)).toMap

  // TODO could make this more specific to turk domains
  implicit val corsSettings = CorsSettings.defaultSettings

  val rejectionHandler = corsRejectionHandler withFallback RejectionHandler.default

  // we use the akka-http routing DSL to specify the server's behavior
  def route =
    cors(corsSettings) {
      handleRejections(rejectionHandler) {
        pathPrefix("task" / Segment) { taskKey =>
          val taskSpecOpt = taskIndex.get(taskKey)
          path("preview") {
            extractScheme { scheme =>
              parameter('https.as[Boolean].?, 'n.as[Int].?) { (httpsOverrideOpt, nOpt) =>
                val shouldUseHttps = httpsOverrideOpt.getOrElse(scheme == "https")
                rejectEmptyResponse {
                  complete {
                    taskSpecOpt.flatMap { taskSpec =>
                      taskSpec.samplePrompts.lift(nOpt.getOrElse(0)).map { prompt =>
                        HttpEntity(
                          ContentTypes.`text/html(UTF-8)`,
                          taskSpec.createTaskHTMLPage(prompt, shouldUseHttps)
                        )
                      }
                    }
                  }
                }
              }
            }
          } ~ path("websocket") {
            taskSpecOpt match {
              case None =>
                logger.warn(s"Got websocket request for task $taskKey which matches no task")
                handleWebSocketMessages(Flow[Message].filter(_ => false))
              case Some(taskSpec) =>
                handleWebSocketMessages(websocketFlow(taskSpec))
            }
          } ~ (post & path("ajax")) {
            entity(as[String]) { e =>
              complete {
                taskSpecOpt.flatMap { taskSpec =>
                  scala.util.Try {
                    import taskSpec.ajaxRequestReader
                    val request = read[taskSpec.AjaxRequest](e)
                    val responseWriter = taskSpec.ajaxResponseWriter.getWriter(request)
                    val response = taskSpec.ajaxService.processRequest(request)
                    HttpEntity(
                      ContentTypes.`text/html(UTF-8)`,
                      write(response)(responseWriter))
                  }.toOption
                }
              }
            }
          }
        } ~ getFromResourceDirectory("")

      }
    }

  // task-specific flow for a websocket connection with a client
  private[this] def websocketFlow(taskSpec: TaskSpecification): Flow[Message, Message, Any] = {
    import taskSpec._ // to import WebsocketRequest and WebsocketResponse types and serializers
    Flow[Message].map {
      case TextMessage.Strict(msg) =>
        Future.successful(List(read[HeartbeatingWebSocketMessage[WebsocketRequest]](msg)))
      case TextMessage.Streamed(stream) => stream // necessary to handle large messages
          .limit(10000)                 // Max frames we are willing to wait for
          .completionTimeout(5 seconds) // Max time until last frame
          .runFold("")(_ + _)           // Merges the frames
          .flatMap(msg => Future.successful(List(read[HeartbeatingWebSocketMessage[WebsocketRequest]](msg))))
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)
        Future.successful(Nil)
    }.mapAsync(parallelism = 3)(identity).mapConcat(identity)
      .collect { case WebSocketMessage(request) => request } // ignore heartbeats
      .via(taskSpec.apiFlow) // this is the key line that delegates to task-specific logic
      .map(WebSocketMessage(_): HeartbeatingWebSocketMessage[WebsocketResponse])
      .keepAlive(30 seconds, () => Heartbeat) // send heartbeat every 30 seconds to keep connection alive
      .map(message => TextMessage.Strict(write[HeartbeatingWebSocketMessage[WebsocketResponse]]((message))))
  }
}
