package spacro.tasks

import spacro._
import spacro.util._

import com.amazonaws.services.mturk.model.AssignmentStatus
import com.amazonaws.services.mturk.model.UpdateExpirationForHITRequest
import com.amazonaws.services.mturk.model.DeleteHITRequest
import com.amazonaws.services.mturk.model.ApproveAssignmentRequest
import com.amazonaws.services.mturk.model.RejectAssignmentRequest

import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.Actor
import akka.actor.ActorRef

import upickle.default._

import com.typesafe.scalalogging.StrictLogging

/**
  * Manages a particular kind of task; corresponds to a single TaskSpecification / HIT Type.
  * In here will be all of the logic related to how to review HITs, do quality control, keep track of auxiliary data,
  * schedule which HITs should be uploaded when, etc.
  */
abstract class HITManager[Prompt, Response](
  helper: HITManager.Helper[Prompt, Response]
) extends Actor {

  import helper.Message._

  final override def receive = receiveHelperMessage orElse receiveAux

  // delegates to helper when given a standard message defined in the helper
  private[this] final val receiveHelperMessage: PartialFunction[Any, Unit] = {
    case ExpireAll    => helper.expireAll
    case DeleteAll    => helper.deleteAll
    case ReviewHITs   => reviewHITs
    case AddPrompt(p) => addPrompt(p)
  }

  /** Override to add more incoming message types and message-processing logic */
  def receiveAux: PartialFunction[Any, Unit] =
    PartialFunction.empty[Any, Unit]

  /** Queries Turk and refreshes the task state, sending assignments for approval/validation,
    * approving/rejecting them, deleting HITs, etc. as necessary */
  def reviewHITs: Unit

  /** Adds a prompt to the set of prompts that this HITManager should be responsible for sourcing responses for. */
  def addPrompt(prompt: Prompt): Unit
}

object HITManager {

  /** Manages the ongoing state for a task with a particular HIT type;
    * keeps track of HITs and assignments that are active, saved, etc.
    * and gives convenience methods for interfacing with Turk. */
  class Helper[P, R](val taskSpec: TaskSpecification { type Prompt = P; type Response = R })(
    implicit val promptReader: Reader[P],
    val responseReader: Reader[R],
    val responseWriter: Writer[R],
    val config: TaskConfig
  ) extends StrictLogging {
    private type Prompt = P
    private type Response = R

    import scala.collection.mutable

    object Message {
      sealed trait Message
      case object DeleteAll extends Message
      case object ExpireAll extends Message
      case object ReviewHITs extends Message
      case class AddPrompt(prompt: Prompt) extends Message
    }
    import Message._
    import taskSpec.hitTypeId

    def expireAll: Unit = {
      val currentlyActiveHITs = activeHITs.iterator.toList
      currentlyActiveHITs.foreach(expireHIT)
    }

    def deleteAll: Unit = {
      val currentlyActiveHITs = activeHITs.iterator.toList
      currentlyActiveHITs.foreach(deleteHIT)
    }

    // HITs Active stuff

    // active HITs are currently up on Turk
    // finished means the HIT is not on turk (i.e., all assignments are done)
    // actives by prompt includes HITs for which some assignments are done and some are not
    private[this] val (activeHITs, finishedHITInfosByPrompt, activeHITInfosByPrompt) = {
      val active = mutable.Set.empty[HIT[Prompt]]
      for {
        mTurkHIT <- config.service.listAllHITs
        if mTurkHIT.getHITTypeId.equals(hitTypeId)
        hit <- config.hitDataService
          .getHIT[Prompt](hitTypeId, mTurkHIT.getHITId)
          .toOptionLogging(logger)
      } yield (active += hit)

      val finishedRes = mutable.Map.empty[Prompt, List[HITInfo[Prompt, Response]]]
      val activeRes = mutable.Map.empty[Prompt, List[HITInfo[Prompt, Response]]]
      config.hitDataService
        .getAllHITInfo[Prompt, Response](hitTypeId)
        .get
        .groupBy(_.hit.prompt)
        .foreach {
          case (prompt, infos) =>
            infos.foreach { hitInfo =>
              if (active.contains(hitInfo.hit)) {
                activeRes.put(prompt, hitInfo :: activeRes.get(prompt).getOrElse(Nil))
              } else {
                finishedRes.put(prompt, hitInfo :: activeRes.get(prompt).getOrElse(Nil))
              }
            }
        }
      (active, finishedRes, activeRes)
    }

    def finishedHITInfosByPromptIterator: Iterator[(Prompt, List[HITInfo[Prompt, Response]])] =
      finishedHITInfosByPrompt.iterator

    def finishedHITInfos(p: Prompt): List[HITInfo[Prompt, Response]] =
      finishedHITInfosByPrompt.get(p).getOrElse(Nil)

    def activeHITInfosByPromptIterator: Iterator[(Prompt, List[HITInfo[Prompt, Response]])] =
      activeHITInfosByPrompt.iterator

    def activeHITInfos(p: Prompt): List[HITInfo[Prompt, Response]] =
      activeHITInfosByPrompt.get(p).getOrElse(Nil)

    def allCurrentHITInfosByPromptIterator: Iterator[(Prompt, List[HITInfo[Prompt, Response]])] =
      activeHITInfosByPromptIterator ++ finishedHITInfosByPromptIterator

    def allCurrentHITInfos(p: Prompt): List[HITInfo[Prompt, Response]] =
      activeHITInfos(p) ++ finishedHITInfos(p)

    /** Create a HIT with the specific parameters.
      * This should be used in order to ensure the helper has a consistent state.
      */
    def createHIT(prompt: Prompt, numAssignments: Int): Try[HIT[Prompt]] = {
      val attempt = taskSpec.createHIT(prompt, numAssignments)
      attempt match {
        case Success(hit) =>
          activeHITs += hit
          val newHITInfo = HITInfo[Prompt, Response](hit, Nil)
          activeHITInfosByPrompt.put(prompt, newHITInfo :: activeHITInfos(prompt))
          logger.info(
            s"Created HIT: ${hit.hitId}\n${config.workerUrl}/mturk/preview?groupId=${hit.hitTypeId}"
          )
        case Failure(e) =>
          logger.error(e.getMessage)
          e.printStackTrace
      }
      attempt
    }

    def isActive(prompt: Prompt): Boolean = activeHITInfosByPrompt.contains(prompt)
    def isActive(hit: HIT[Prompt]): Boolean = activeHITs.contains(hit)
    def isActive(hitId: String): Boolean = activeHITs.exists(_.hitId == hitId)
    def numActiveHITs = activeHITs.size

    def expireHIT(hit: HIT[Prompt]): Unit = {
      val cal = java.util.Calendar.getInstance
      cal.add(java.util.Calendar.DATE, -1)
      val yesterday = cal.getTime
      Try(
        config.service.updateExpirationForHIT(
          (new UpdateExpirationForHITRequest)
            .withHITId(hit.hitId)
            .withExpireAt(yesterday)
        )
      ) match {
        case Success(_) =>
          logger.info(s"Expired HIT: ${hit.hitId}\nHIT type for expired HIT: ${hitTypeId}")
        case Failure(e) =>
          logger.error(s"HIT expiration failed:\n$hit\n$e")
      }
    }

    /** Deletes a HIT (if possible) and takes care of bookkeeping. */
    def deleteHIT(hit: HIT[Prompt]): Unit = {
      Try(config.service.deleteHIT((new DeleteHITRequest).withHITId(hit.hitId))) match {
        case Success(_) =>
          logger.info(s"Deleted HIT: ${hit.hitId}\nHIT type for deleted HIT: ${hitTypeId}")
          if (!isActive(hit)) {
            logger.error(s"Deleted HIT that isn't registered as active: $hit")
          } else {
            activeHITs -= hit
            // add to other appropriate data structures
            val finishedData = finishedHITInfos(hit.prompt)
            val activeData = activeHITInfos(hit.prompt)
            val curInfo = activeData
              .find(_.hit.hitId == hit.hitId)
              .getOrElse {
                logger.error("Could not find active HIT to move to finished");
                HITInfo(
                  hit,
                  config.hitDataService.getAssignmentsForHIT[Response](hitTypeId, hit.hitId).get
                )
              }
            val newActiveData = activeData.filterNot(_.hit.hitId == hit.hitId)
            val newFinishedData = curInfo :: finishedData
            if (newActiveData.isEmpty) {
              activeHITInfosByPrompt.remove(hit.prompt)
            } else {
              activeHITInfosByPrompt.put(hit.prompt, newActiveData)
            }
            finishedHITInfosByPrompt.put(hit.prompt, newFinishedData)
          }
        case Failure(e) =>
          logger.error(s"HIT deletion failed:\n$hit\n$e")
      }
    }

    // Assignment reviewing

    /** Represents an assignment waiting for a reviewing result. */
    class AssignmentInReview protected[Helper] (val assignment: Assignment[Response])

    private[this] val assignmentsInReview = mutable.Set.empty[AssignmentInReview]

    def getInReview(assignment: Assignment[Response]): Option[AssignmentInReview] =
      assignmentsInReview.find(_.assignment == assignment)

    def getInReview(assignmentId: String): Option[AssignmentInReview] =
      assignmentsInReview.find(_.assignment.assignmentId == assignmentId)

    def isInReview(assignment: Assignment[Response]): Boolean =
      getInReview(assignment).nonEmpty

    def isInReview(assignmentId: String): Boolean =
      getInReview(assignmentId).nonEmpty
    def numAssignmentsInReview = assignmentsInReview.size

    /** Mark an assignment as under review. */
    def startReviewing(assignment: Assignment[Response]): AssignmentInReview = {
      val aInRev = new AssignmentInReview(assignment)
      assignmentsInReview += aInRev
      aInRev
    }

    /** Process and record the result of reviewing an assignment. */
    def evaluateAssignment(
      hit: HIT[Prompt],
      aInRev: AssignmentInReview,
      evaluation: AssignmentEvaluation
    ): Unit = {
      import aInRev.assignment
      evaluation match {
        case Approval(message) =>
          Try {
            config.service.approveAssignment(
              (new ApproveAssignmentRequest)
                .withAssignmentId(assignment.assignmentId)
                .withRequesterFeedback(message)
            )
            assignmentsInReview -= aInRev
            val curData = activeHITInfos(hit.prompt)
            val curInfo = curData
              .find(_.hit.hitId == hit.hitId)
              .getOrElse {
                logger.error(s"Could not find active data for hit $hit")
                activeHITs += hit
                HITInfo[Prompt, Response](hit, Nil)
              }
            val filteredData = curData.filterNot(_.hit.hitId == hit.hitId)
            val newInfo = curInfo.copy(assignments = assignment :: curInfo.assignments)
            activeHITInfosByPrompt.put(hit.prompt, newInfo :: filteredData)
            logger.info(
              s"Approved assignment for worker ${assignment.workerId}: ${assignment.assignmentId}\n" +
              s"HIT for approved assignment: ${assignment.hitId}; $hitTypeId"
            )
            config.hitDataService.saveApprovedAssignment(assignment).recover {
              case e =>
                logger.error(s"Failed to save approved assignment; data:\n${write(assignment)}")
            }
          }
        case Rejection(message) =>
          Try {
            config.service.rejectAssignment(
              (new RejectAssignmentRequest)
                .withAssignmentId(assignment.assignmentId)
                .withRequesterFeedback(message)
            )
            assignmentsInReview -= aInRev
            logger.info(
              s"Rejected assignment: ${assignment.assignmentId}\n" +
              s"HIT for rejected assignment: ${assignment.hitId}; ${hitTypeId}\n" +
              s"Reason: $message"
            )
            config.hitDataService.saveRejectedAssignment(assignment) recover {
              case e =>
                logger.error(s"Failed to save approved assignment; data:\n${write(assignment)}")
            }
          }
      }
    }
  }
}
