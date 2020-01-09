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

  val prompts = sentences.indices.map(SamplePrompt(_)).toVector

  // If you want a WebSocket connection between the server and client,
  // you must define a Flow (from akka-http).
  // In most cases, however, using ajax is easier and will suffice.
  // Here, we show for completeness how you might map the prompt (a sentence index)
  // to the sentence it maps to in a Flow.
  // But we don't actually use it in this example,
  // hence the usage of the `NoWebsockets` constructor for the TaskSpecification below.

  // lazy val sampleApiFlow = Flow[ApiRequest].map {
  //   case SentenceRequest(id) => SentenceResponse(id, sentences(id))
  // }

  // The easiest way to facilitate communication between the client and server is via Ajax.
  // The DotKleisli type is taken from the jjm utlity library, and it allows for an
  // expressive, typed API to be expressed in a single type;
  // i.e., we can implement a complex API with multiple request and response types
  // using a single object.
  // In this simple example, we only use one request and response type.
  // For more details on how DotKleisli works, ask Julian.
  lazy val sampleAjaxService = new DotKleisli[Id, SampleAjaxRequest] {
    def apply(request: SampleAjaxRequest) =
      SampleAjaxResponse(sentences(request.prompt.id))
  }

  // We need a set of sample prompts for when viewing local version of the task at
  // http://localhost:<http port>/task/<task key>/preview
  // where a URL parameter specified at the end like ?n=1 will show the interface for the
  // sample prompt at index 1 (default is 0).
  // Here, we're just using the full list of prompts.
  val samplePrompts = prompts

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
        .constAssignments[SamplePrompt, SampleResponse](
          helper = helper,
          numAssignmentsPerPrompt = 1,
          initNumHITsToKeepActive = 3,
          _promptSource = prompts.iterator)
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

  // For examples of utility methods which might be useful,
  // or just for a much more complex annotation pipeline,
  // see the annotation pipelines in the QA-SRL Crowdsourcing project, e.g., at
  // https://github.com/julianmichael/qasrl-crowdsourcing/blob/master/qasrl-crowd/src-jvm/qasrl/crowd/QASRLAnnotationPipeline.scala#L530

}
