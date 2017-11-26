package spacro.tasks

import scalajs.js
import scalajs.js.JSApp
import org.scalajs.jquery.jQuery
import org.scalajs.dom

import scala.concurrent.Future

import upickle.default._

/** Superclass for an implementation of a client/interface for a turk task.
  * Gives access by field to all of the information written into the `TaskPage` on the server.
  */
abstract class TaskClient[
  Prompt : Reader,
  Response : Writer,
  AjaxRequest <: { type Response } : Writer : ResponseReader
] {
  import scala.scalajs.js.Dynamic.global

  lazy val assignmentIdOpt: Option[String] = {
    global.turkSetAssignmentID()
    jQuery("#assignmentId").attr("value").toOption.filter(_ != "ASSIGNMENT_ID_NOT_AVAILABLE")
  }

  lazy val isNotAssigned = assignmentIdOpt.isEmpty

  lazy val workerIdOpt: Option[String] = {
    Option(global.turkGetParam("workerId", "UNASSIGNED").asInstanceOf[String]).filter(_ != "UNASSIGNED")
  }

  import FieldLabels._

  lazy val taskKey: String = {
    read[String](jQuery(s"#$taskKeyLabel").attr("value").get)
  }

  lazy val serverDomain: String = {
    read[String](jQuery(s"#$serverDomainLabel").attr("value").get)
  }

  lazy val httpPort: Int = {
    read[Int](jQuery(s"#$httpPortLabel").attr("value").get)
  }

  lazy val httpsPort: Int = {
    read[Int](jQuery(s"#$httpsPortLabel").attr("value").get)
  }

  lazy val ajaxUri = {
    val isHttps = dom.document.location.protocol == "https:"
    val ajaxHttpProtocol = if (isHttps) "https" else "http"
    val serverPort = if(isHttps) httpsPort else httpPort
    s"$ajaxHttpProtocol://$serverDomain:$serverPort/task/$taskKey/ajax"
  }

  def makeAjaxRequest(request: AjaxRequest): Future[request.Response] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    dom.ext.Ajax
      .post(url = ajaxUri, data = write(request))
      .map(response => read[request.Response](response.responseText)(
             implicitly[ResponseReader[AjaxRequest]].getReader(request)))
  }

  lazy val websocketUri: String = {
    val isHttps = dom.document.location.protocol == "https:"
    val wsProtocol = if (isHttps) "wss" else "ws"
    val serverPort = if(isHttps) httpsPort else httpPort
    s"$wsProtocol://$serverDomain:$serverPort/task/$taskKey/websocket"
  }

  lazy val prompt: Prompt = {
    read[Prompt](jQuery(s"#$promptLabel").attr("value").get)
  }

  lazy val externalSubmitURL: String = {
    jQuery(s"form#$mturkFormLabel").attr("action").get
  }

  def setResponse(response: Response): Unit = {
    jQuery(s"#$responseLabel").attr("value", write(response))
  }

  def main(): Unit
}
