package spacro.tasks

import jjm.Dot
import jjm.DotDecoder

import scalajs.js
import scalajs.js.JSApp

import org.scalajs.jquery.jQuery
import org.scalajs.dom

import scala.concurrent.Future

import io.circe.{Encoder, Decoder}
import io.circe.syntax._

/** Superclass for an implementation of a client/interface for a turk task.
  * Gives access by field to all of the information written into the `TaskPage` on the server.
  */
abstract class TaskClient[
  Prompt: Decoder,
  Response: Encoder,
  AjaxRequest <: Dot : Encoder : DotDecoder
] {
  import scala.scalajs.js.Dynamic.global
  import io.circe.parser._

  lazy val assignmentIdOpt: Option[String] = {
    global.turkSetAssignmentID()
    jQuery("#assignmentId").attr("value").toOption.filter(_ != "ASSIGNMENT_ID_NOT_AVAILABLE")
  }

  lazy val isNotAssigned = assignmentIdOpt.isEmpty

  lazy val workerIdOpt: Option[String] = {
    Option(global.turkGetParam("workerId", "UNASSIGNED").asInstanceOf[String])
      .filter(_ != "UNASSIGNED")
  }

  import FieldLabels._

  lazy val taskKey: String = {
    decode[String](jQuery(s"#$taskKeyLabel").attr("value").get).right.get
  }

  lazy val serverDomain: String = {
    decode[String](jQuery(s"#$serverDomainLabel").attr("value").get).right.get
  }

  lazy val httpPort: Int = {
    decode[Int](jQuery(s"#$httpPortLabel").attr("value").get).right.get
  }

  lazy val httpsPort: Int = {
    decode[Int](jQuery(s"#$httpsPortLabel").attr("value").get).right.get
  }

  lazy val ajaxUri = {
    val isHttps = dom.document.location.protocol == "https:"
    val ajaxHttpProtocol = if (isHttps) "https" else "http"
    val serverPort = if (isHttps) httpsPort else httpPort
    s"$ajaxHttpProtocol://$serverDomain:$serverPort/task/$taskKey/ajax"
  }

  private[this] val printer = io.circe.Printer.noSpaces
  import io.circe.syntax._

  def makeAjaxRequest(request: AjaxRequest): Future[request.Out] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    dom.ext.Ajax
      .post(url = ajaxUri, data = printer.pretty(request.asJson))
      .map { response =>
        decode[request.Out](response.responseText)(
          implicitly[DotDecoder[AjaxRequest]].apply(request)
        ).right.get
      }
  }

  lazy val websocketUri: String = {
    val isHttps = dom.document.location.protocol == "https:"
    val wsProtocol = if (isHttps) "wss" else "ws"
    val serverPort = if (isHttps) httpsPort else httpPort
    s"$wsProtocol://$serverDomain:$serverPort/task/$taskKey/websocket"
  }

  lazy val prompt: Prompt = {
    decode[Prompt](jQuery(s"#$promptLabel").attr("value").get).right.get
  }

  lazy val externalSubmitURL: String = {
    jQuery(s"form#$mturkFormLabel").attr("action").get
  }

  def setResponse(response: Response): Unit = {
    jQuery(s"#$responseLabel").attr("value", printer.pretty(response.asJson))
  }

  def main(): Unit
}
