package spacro.tasks

import spacro._
import spacro.util._

import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Cancellable

import com.typesafe.scalalogging.StrictLogging

/** Actor that behaves as an endpoint for interfacing with the task through the SBT console.
  * The hit manager (ActorRef) passed into the constructor
  * needs to be the one associated with the helper object passed into the constructor.
  */
class TaskManager[Prompt, Response](
  hitManagementHelper: HITManager.Helper[Prompt, Response],
  hitManager: ActorRef
) extends Actor
    with StrictLogging {

  import TaskManager.Message._
  import hitManagementHelper.Message._
  import hitManagementHelper.taskSpec.hitTypeId
  import hitManagementHelper.config

  override def receive = {
    case Start(interval, delay) => start(interval, delay)
    case Stop                   => stop
    case Update                 => update
    case Expire                 => expire
    case Delete                 => delete
  }

  // used to schedule updates once this has started
  private[this] var schedule: Option[Cancellable] = None

  // begin updating / polling the MTurk API
  private[this] def start(interval: FiniteDuration, delay: FiniteDuration): Unit = {
    if (schedule.fold(true)(_.isCancelled)) {
      schedule = Some(
        context.system.scheduler
          .schedule(delay, interval, self, Update)(context.system.dispatcher, self)
      )
    }
  }

  // stop regular polling
  private[this] def stop: Unit = {
    schedule.foreach(_.cancel())
  }

  // stop updating and expire all currently uploaded HITs
  private[this] def expire: Unit = {
    stop
    hitManager ! ExpireAll
  }

  // delete all HITs from the system (reviewing reviewable HITs and approving other pending assignments)
  private[this] def delete: Unit = {
    stop
    update
    hitManager ! DeleteAll
  }

  // review assignments, delete completed HITs, and upload new HITs
  private[this] def update: Unit = {
    logger.info(s"Updating (${hitTypeId})...")
    hitManager ! ReviewHITs
    hitManager ! ReviewHITs
  }
}

object TaskManager {

  object Message {
    sealed trait Message
    case class Start(interval: FiniteDuration, delay: FiniteDuration = 2 seconds) extends Message
    case object Stop extends Message
    case object Update extends Message
    case object Expire extends Message
    case object Delete extends Message
  }
}
