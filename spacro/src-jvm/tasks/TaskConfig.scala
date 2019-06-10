package spacro.tasks

import spacro.HITDataService

import akka.actor.ActorSystem

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.mturk.AmazonMTurk
import com.amazonaws.services.mturk.AmazonMTurkClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration

/** Contains the global configuration of our usage of the MTurk API,
  * including relevant values (URLs, API hooks) and whether we are running
  * on production or in the sandbox.
  */
sealed trait TaskConfig {

  /** The API hook with which we communicate with MTurk.
    * We need a different hook depending on whether we're in sandbox or production,
    * because it uses a different URL.
    */
  val service: AmazonMTurk

  /* The URL at which you can access and work on a group of HITs if you know the HIT Type ID. */
  val workerUrl: String

  /** The URL used by HTMLQuestion and ExternalQuestion question types to submit assignments.
    * (See http://docs.aws.amazon.com/AWSMechTurk/latest/AWSMturkAPI/ApiReference_QuestionAnswerDataArticle.html
    * for documentation on these question types.)
    * In particular, if we want to provide our own HTML with which to render the task (which we usually do),
    * instead of using the default "Submit HIT" button, we must make our own HTML form and embed it in the HIT.
    * That form then needs to submit to this URL.
    */
  val externalSubmitURL: String

  /** Whether we are working in production as opposed to the sandbox. */
  val isProduction: Boolean

  /** The ActorSystem we're using to manage tasks and host the server. */
  val actorSystem: ActorSystem

  /** The domain at which we're hosting our server. */
  val serverDomain: String

  /** The interface (IP address) we're using to host the server. */
  val interface: String

  /** What port we're hosting HTTP at. */
  val httpPort: Int

  /** What port we're hosting HTTPS at. */
  val httpsPort: Int

  /** Service for storing and getting finished HIT data */
  val hitDataService: HITDataService

  /** Name of the project we're building your JS files with; used to determine what JS
    * files the client asks for. Needs to agree with the project name in your `build.sbt` */
  val projectName: String
}

object TaskConfig {
  val sandboxEndpointUrl = "https://mturk-requester-sandbox.us-east-1.amazonaws.com"
  val endpointRegion = Regions.US_EAST_1
}

/** Complete configuration for running on production. */
case class ProductionTaskConfig(
  override val projectName: String,
  override val serverDomain: String,
  override val interface: String,
  override val httpPort: Int,
  override val httpsPort: Int,
  override val hitDataService: HITDataService
) extends TaskConfig {

  // production endpoint configuration is chosen by default
  override val service: AmazonMTurk = AmazonMTurkClientBuilder.standard
    .withRegion(TaskConfig.endpointRegion)
    .build

  override val workerUrl = "https://www.mturk.com"

  override val externalSubmitURL = "https://www.mturk.com/mturk/externalSubmit"

  override val isProduction = true

  override val actorSystem = ActorSystem("production")
}

/** Complete configuration for running on the sandbox. */
case class SandboxTaskConfig(
  override val projectName: String,
  override val serverDomain: String,
  override val interface: String,
  override val httpPort: Int,
  override val httpsPort: Int,
  override val hitDataService: HITDataService
) extends TaskConfig {

  override val service: AmazonMTurk = AmazonMTurkClientBuilder.standard
    .withEndpointConfiguration(
      new EndpointConfiguration(TaskConfig.sandboxEndpointUrl, TaskConfig.endpointRegion.getName)
    )
    .build

  override val workerUrl = "https://workersandbox.mturk.com"

  override val externalSubmitURL = "https://workersandbox.mturk.com/mturk/externalSubmit"

  override val isProduction = false

  override val actorSystem = ActorSystem("sandbox")
}
