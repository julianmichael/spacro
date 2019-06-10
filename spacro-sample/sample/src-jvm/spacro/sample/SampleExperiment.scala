package spacro.sample

import jjm.DotKleisli

import cats.Id

import spacro._
import spacro.tasks._
import spacro.util._

import akka.actor._
import akka.stream.scaladsl.Flow

import scala.concurrent.duration._
import scala.language.postfixOps

/** Home for all of the code to set up, run, and manage the sample task.
  * The code in this class is well-documented with comments explaining what is going on.
  */
class SampleExperiment(implicit config: TaskConfig) {

  // you need to define a HIT Type for each task you will be running on Turk.
  val sampleHITType = HITType(
    title = s"Sample task: is this sentence good?",
    description = s"""
      Given a sentence, indicate whether it is good.
    """.trim,
    reward = 0.10,
    keywords = "language,english,question answering",
    autoApprovalDelay = 2592000L,
    assignmentDuration = 600L
  )

  // Then, you need some source of prompts which you will get responses to over turk.
  // in the sample task, these prompts are just strings.
  val sentences = List("Hello, this is a sentence.", "This is another sentence.")

  // you must define a Flow (type is from akka-http) for this task, which will determine how
  // the server responds to WebSocket messages from clients.
  // Here, we map the prompt (a sentence index) to the sentence it maps to.
  // lazy val sampleApiFlow = Flow[ApiRequest].map {
  //   case SentenceRequest(id) => SentenceResponse(id, sentences(id))
  // }

  lazy val sampleAjaxService = new DotKleisli[Id, SampleAjaxRequest] {
    def apply(request: SampleAjaxRequest) =
      SampleAjaxResponse(sentences(request.prompt.id))
  }

  // you also need a sample prompt for when you view the local version of the task at
  // http://localhost:<http port>/task/<task key>/preview
  val samplePrompts = Vector(SamplePrompt(0))

  // the task specification is defined on the basis of the above fields
  lazy val taskSpec =
    TaskSpecification.NoWebsockets[SamplePrompt, SampleResponse, SampleAjaxRequest](
      sampleTaskKey,
      sampleHITType,
      sampleAjaxService,
      samplePrompts
    )

  // you will probably always construct a HITManager.Helper in this way for your HITManager instance
  // that will coordinate the reviewing and uploading of assignments and HITs.
  lazy val helper = new HITManager.Helper(taskSpec)

  import config.actorSystem

  // the sample task just uses a NumAssignmentsHITManager, which coordinates so that
  // it gets a fixed number of approved assignments for every HIT.
  lazy val hitManager = actorSystem.actorOf(
    Props(
      NumAssignmentsHITManager
        .constAssignments[SamplePrompt, SampleResponse](helper, 1, 3, samplePrompts.iterator)
    )
  )

  // then you create the web server which will host the service
  // (the server MUST BE LIVE as long as the HITs are on Turk)
  lazy val server = new Server(List(taskSpec))

  // and the actor that you will send messages to in order to manually manage the task
  // (e.g., telling it to start/stop reviewing/uploading HITs, telling it to disable all HITs, etc.)
  lazy val actor = actorSystem.actorOf(Props(new TaskManager(helper, hitManager)))

  // and finally you will probably want some convenience methods for controlling the task from the console.
  import TaskManager.Message._

  def start(interval: FiniteDuration = 1 minute) = {
    server
    actor ! Start(interval)
  }
  def stop = actor ! Stop
  def expire = actor ! Expire
  def delete = actor ! Delete

  def update = {
    server
    actor ! Update
  }

  // beyond that, you will probably want a number of convenience methods for poking around in the data
  // as it is coming in. this will evolve organically as you work through the task design, but you ideally
  // should finalize all of it before the Big Run so that you don't have to shut down the server intermittently
  // to run newly compiled code. (though, realistically, this will probably happen once or twice,
  // so try to be ready to do that if necessary without losing any data.)
}
