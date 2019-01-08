package spacro.tasks

import spacro._
import spacro.util._

import scala.language.higherKinds

import com.amazonaws.services.mturk.model.{HIT => MTurkHIT}
import com.amazonaws.services.mturk.model.{Assignment => MTurkAssignment}
import com.amazonaws.services.mturk.model.QualificationRequirement
import com.amazonaws.services.mturk.model.ReviewPolicy
import com.amazonaws.services.mturk.model.PolicyParameter
import com.amazonaws.services.mturk.model.AssignmentStatus
import com.amazonaws.services.mturk.model.CreateHITTypeRequest
import com.amazonaws.services.mturk.model.CreateHITWithHITTypeRequest
import com.amazonaws.services.mturk.AmazonMTurkClient

import java.util.Calendar

import scala.util.Try
import scala.concurrent.duration._

import akka.stream.scaladsl.Flow

import scalatags.Text.TypedTag

import io.circe.{Encoder, Decoder}

/** Specifies a kind of task to run on MTurk.
  *
  * The code defining an individual task type will be here.
  * An instance of this class will correspond to a single HIT Type ID,
  * which is Mechanical Turk's way of categorizing HITs uploaded to the system.
  * This specifies the method to convert from `Prompt`s
  * (as in, the type parameter seen all over this project) into XML strings
  * that are POSTed to the MTurk API as questions shown to annotators.
  * It also has a method for converting from annotator responses (also XML strings)
  * into `Response`s.
  *
  * To implement the actual logic & interface of a task, the work is done in the client-side code.
  *
  * TaskSpecification is also responsible for holding HIT Type ID of its HIT Type.
  *
  * @tparam Prompt
  * @tparam Response
  */
sealed trait TaskSpecification {
  val taskKey: String
  val hitType: HITType
  implicit val config: TaskConfig

  type Prompt
  implicit val promptEncoder: Encoder[Prompt]
  type Response
  implicit val responseDecoder: Decoder[Response]

  // general message request/response types because messages can be freely passed between client/server
  type WebsocketRequest
  implicit val websocketRequestDecoder: Decoder[WebsocketRequest]
  type WebsocketResponse
  implicit val websocketResponseEncoder: Encoder[WebsocketResponse]

  // response type depends on request type because each response is specific to a request
  type AjaxRequest <: { type Response }
  implicit val ajaxRequestDecoder: Decoder[AjaxRequest]
  implicit val ajaxResponseEncoder: ResponseEncoder[AjaxRequest]

  val samplePrompts: Vector[Prompt]

  val apiFlow: Flow[WebsocketRequest, WebsocketResponse, Any]
  val ajaxService: Service[AjaxRequest]

  val frozenHITTypeId: Option[String]
  val taskPageHeadElements: List[TypedTag[String]]
  val taskPageBodyElements: List[TypedTag[String]]

  private[this] val printer = io.circe.Printer.noSpaces
  import io.circe.parser._

  /** The HIT Type ID for this task.
    *
    * When this is accessed with a certain set of parameters for the first time,
    * a new HIT Type ID will be registered on Amazon's systems.
    * Subsequent calls with the same parameters will always return this same value,
    * for the life of the HIT Type (which I believe expires 30 days after the last time it is used.
    * It may be 90 days. TODO check on that. But it doesn't really matter...)
    *
    * I'm not 100% sure this needs to be lazy... but it's not hurting anyone as it is.
    */
  final lazy val hitTypeId = frozenHITTypeId.getOrElse(
    config.service
      .createHITType(
        (new CreateHITTypeRequest)
          .withAutoApprovalDelayInSeconds(hitType.autoApprovalDelay)
          .withAssignmentDurationInSeconds(hitType.assignmentDuration)
          .withReward(f"${hitType.reward}%.2f")
          .withTitle(hitType.title)
          .withKeywords(hitType.keywords)
          .withDescription(hitType.description)
          .withQualificationRequirements(hitType.qualRequirements: _*)
      )
      .getHITTypeId
  )

  /** Creates a HIT on MTurk.
    *
    * If the HIT is successfully created, saves the HIT to disk and returns it.
    * Otherwise returns a Failure with the error.
    *
    * @param prompt the data from which to generate the question for the HIT
    * @return the created HIT, wrapped in a Try in case of error
    */
  final def createHIT(
    prompt: Prompt,
    numAssignments: Int,
    lifetime: Long = 2592000L /* seconds (30 days) */
  ): Try[HIT[Prompt]] = {

    val questionXML = createQuestionXML(prompt)

    // just hash the time and main stuff of our request for the unique token.
    val uniqueRequestToken = (hitTypeId, questionXML, System.nanoTime()).toString.hashCode.toString

    // NOTE: don't bother with requester annotation---we don't get it back and it causes errors if >255 bytes (which was documented NOWHERE)
    for {
      hitCreationResult <- Try(
        config.service.createHITWithHITType(
          (new CreateHITWithHITTypeRequest)
            .withHITTypeId(hitTypeId)
            .withQuestion(questionXML)
            .withLifetimeInSeconds(lifetime)
            .withMaxAssignments(numAssignments)
            .withUniqueRequestToken(uniqueRequestToken)
        )
      )
      hit = HIT(
        hitTypeId,
        hitCreationResult.getHIT.getHITId,
        prompt,
        hitCreationResult.getHIT.getCreationTime.getTime
      )
      _ <- config.hitDataService.saveHIT(hit)
    } yield hit
  }

  /** Extracts the annotator's response from an "answer" XML object retrieved from the MTurk API
    * after the completion of an assignment.
    *
    * See http://docs.aws.amazon.com/AWSMechTurk/latest/AWSMturkAPI/ApiReference_QuestionAnswerDataArticle.html
    * for a specification of the XML documents that may be received from the API as answers.
    * There are helpful classes in the Java API for parsing this XML; see implementations of this method
    * for examples.
    *
    * @param answerXML the XML string received from the API
    * @return the well-typed data representation of an annotator response
    */
  final def extractResponse(answerXML: String): Response =
    decode[Response](getAnswers(answerXML)(FieldLabels.responseLabel)).right.get

  /** Extracts the annotator's feedback from an answer XML string.
    *
    * The feedback field needs to be manually included in the form on the client in order for this to work.
    * (Otherwise, this just returns the empty string.)
    * Notes from the documentation for `extractResponse` apply here.
    *
    * @param answerXML the XML string received from the API
    * @return the annotator's feedback
    */
  final def extractFeedback(answerXML: String): String =
    getAnswers(answerXML).get(FieldLabels.feedbackLabel).getOrElse("")

  /** Makes an Assignment data structure corresponding to a completed assignment on MTurk.
    * Does not save it to disk since it hasn't been reviewed yet.
    * TODO: this should create some sort of "reviewable assignment" instead, which perhaps
    * can be saved immediately to avoid possible problems, and which will help ensure everything
    * gets reviewed as appropriate.
    */
  final def makeAssignment(hitId: String, mTurkAssignment: MTurkAssignment): Assignment[Response] =
    Assignment(
      hitTypeId = hitTypeId,
      hitId = hitId,
      assignmentId = mTurkAssignment.getAssignmentId,
      workerId = mTurkAssignment.getWorkerId,
      acceptTime = mTurkAssignment.getAcceptTime.getTime,
      submitTime = mTurkAssignment.getSubmitTime.getTime,
      response = extractResponse(mTurkAssignment.getAnswer),
      feedback = extractFeedback(mTurkAssignment.getAnswer)
    )

  final def createTaskHTMLPage(prompt: Prompt, useHttps: Boolean): String =
    TaskPage
      .htmlPage(
        prompt,
        this,
        useHttps = useHttps,
        headTags = taskPageHeadElements,
        bodyEndTags = taskPageBodyElements
      )
      .render

  // == Private methods and fields ==

  // auxiliary method for extracting response and feedback
  private[this] final def getAnswers(answerXML: String): Map[String, String] = {
    (scala.xml.XML.loadString(answerXML) \ "Answer").toList
      .map((x: scala.xml.Node) => (x \ "QuestionIdentifier").text -> (x \ "FreeText").text)
      .toMap
  }

  /** Creates the "question" XML object to send to the MTurk API when creating a HIT.
    *
    * See http://docs.aws.amazon.com/AWSMechTurk/latest/AWSMturkAPI/ApiReference_QuestionAnswerDataArticle.html
    * for a specification of the XML documents that may be sent to the API as questions.
    * The result should include the full text giving instructions on how to do the task,
    * whereas the Prompt object should contain only the information necessary for a single specific question.
    *
    * @param prompt the well-typed data representation of a question
    * @return the MTurk-ready XML representation of a question
    */
  private[this] final def createQuestionXML(prompt: Prompt): String = {
    s"""
      <?xml version="1.0" encoding="UTF-8"?>
      <HTMLQuestion xmlns="http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2011-11-11/HTMLQuestion.xsd">
        <HTMLContent><![CDATA[
          <!DOCTYPE html>${createTaskHTMLPage(prompt, useHttps = true)}
        ]]></HTMLContent>
        <FrameHeight>600</FrameHeight>
      </HTMLQuestion>
    """.trim
  }
}

object TaskSpecification {

  type NoApi = NoAjax with NoWebsockets

  object NoApi {

    def apply[P, R](
      taskKey: String,
      hitType: HITType,
      samplePrompts: Vector[P],
      frozenHITTypeId: Option[String] = None,
      taskPageHeadElements: List[TypedTag[String]] = Nil,
      taskPageBodyElements: List[TypedTag[String]] = Nil
    )(
      implicit promptEncoder: Encoder[P],
      responseDecoder: Decoder[R],
      config: TaskConfig
    ): NoApi {
      type Prompt = P; type Response = R;
    } =
      TaskSpecificationImpl[P, R, Unit, Unit, Service.UnitRequest](
        taskKey,
        hitType,
        Flow[Unit],
        Service.unitServer,
        samplePrompts,
        frozenHITTypeId,
        taskPageHeadElements,
        taskPageBodyElements
      )
  }

  type NoWebsockets = TaskSpecification {
    type WebsocketRequest = Unit; type WebsocketResponse = Unit
  }

  object NoWebsockets {

    def apply[P, R, AjaxReq <: { type Response }](
      taskKey: String,
      hitType: HITType,
      ajaxService: Service[AjaxReq],
      samplePrompts: Vector[P],
      frozenHITTypeId: Option[String] = None,
      taskPageHeadElements: List[TypedTag[String]] = Nil,
      taskPageBodyElements: List[TypedTag[String]] = Nil
    )(
      implicit promptEncoder: Encoder[P],
      responseDecoder: Decoder[R],
      ajaxRequestDecoder: Decoder[AjaxReq],
      ajaxResponseEncoder: ResponseEncoder[AjaxReq],
      config: TaskConfig
    ): NoWebsockets {
      type Prompt = P; type Response = R;
      type AjaxRequest = AjaxReq;
    } =
      TaskSpecificationImpl[P, R, Unit, Unit, AjaxReq](
        taskKey,
        hitType,
        Flow[Unit],
        ajaxService,
        samplePrompts,
        frozenHITTypeId,
        taskPageHeadElements,
        taskPageBodyElements
      )
  }

  type NoAjax = TaskSpecification { type AjaxRequest = Service.UnitRequest }

  object NoAjax {

    def apply[P, R, WebsocketReq, WebsocketResp](
      taskKey: String,
      hitType: HITType,
      apiFlow: Flow[WebsocketReq, WebsocketResp, Any],
      samplePrompts: Vector[P],
      frozenHITTypeId: Option[String] = None,
      taskPageHeadElements: List[TypedTag[String]] = Nil,
      taskPageBodyElements: List[TypedTag[String]] = Nil
    )(
      implicit promptEncoder: Encoder[P],
      responseDecoder: Decoder[R],
      websocketRequestDecoder: Decoder[WebsocketReq],
      websocketResponseEncoder: Encoder[WebsocketResp],
      config: TaskConfig
    ): NoAjax {
      type Prompt = P; type Response = R;
      type WebsocketRequest = WebsocketReq; type WebsocketResponse = WebsocketResp;
    } =
      TaskSpecificationImpl[P, R, WebsocketReq, WebsocketResp, Service.UnitRequest](
        taskKey,
        hitType,
        apiFlow,
        Service.unitServer,
        samplePrompts,
        frozenHITTypeId,
        taskPageHeadElements,
        taskPageBodyElements
      )
  }

  private[this] case class TaskSpecificationImpl[
    P,
    R,
    WebsocketReq,
    WebsocketResp,
    AjaxReq <: { type Response }
  ](
    override val taskKey: String,
    override val hitType: HITType,
    override val apiFlow: Flow[WebsocketReq, WebsocketResp, Any],
    override val ajaxService: Service[AjaxReq],
    override val samplePrompts: Vector[P],
    override val frozenHITTypeId: Option[String],
    override val taskPageHeadElements: List[TypedTag[String]],
    override val taskPageBodyElements: List[TypedTag[String]]
  )(
    implicit override val promptEncoder: Encoder[P],
    override val responseDecoder: Decoder[R],
    override val websocketRequestDecoder: Decoder[WebsocketReq],
    override val websocketResponseEncoder: Encoder[WebsocketResp],
    override val ajaxRequestDecoder: Decoder[AjaxReq],
    override val ajaxResponseEncoder: ResponseEncoder[AjaxReq],
    override val config: TaskConfig
  ) extends TaskSpecification {

    override type Prompt = P
    override type Response = R
    override type WebsocketRequest = WebsocketReq
    override type WebsocketResponse = WebsocketResp
    override type AjaxRequest = AjaxReq
  }

  def apply[P, R, WebsocketReq, WebsocketResp, AjaxReq <: { type Response }](
    taskKey: String,
    hitType: HITType,
    apiFlow: Flow[WebsocketReq, WebsocketResp, Any],
    ajaxService: Service[AjaxReq],
    samplePrompts: Vector[P],
    frozenHITTypeId: Option[String] = None,
    taskPageHeadElements: List[TypedTag[String]] = Nil,
    taskPageBodyElements: List[TypedTag[String]] = Nil
  )(
    implicit promptEncoder: Encoder[P],
    responseDecoder: Decoder[R],
    websocketRequestDecoder: Decoder[WebsocketReq],
    websocketResponseEncoder: Encoder[WebsocketResp],
    ajaxRequestDecoder: Decoder[AjaxReq],
    ajaxResponseEncoder: ResponseEncoder[AjaxReq],
    config: TaskConfig
  ): TaskSpecification {
    type Prompt = P; type Response = R;
    type WebsocketRequest = WebsocketReq; type WebsocketResponse = WebsocketResp;
    type AjaxRequest = AjaxReq
  } =
    TaskSpecificationImpl[P, R, WebsocketReq, WebsocketResp, AjaxReq](
      taskKey,
      hitType,
      apiFlow,
      ajaxService,
      samplePrompts,
      frozenHITTypeId,
      taskPageHeadElements,
      taskPageBodyElements
    )
}
