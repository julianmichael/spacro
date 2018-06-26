package spacro.tasks

import spacro._
import spacro.util._

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

import upickle.default.Reader

import akka.actor.ActorRef

import com.amazonaws.services.mturk.model.AssignmentStatus
import com.amazonaws.services.mturk.model.HITStatus
import com.amazonaws.services.mturk.model.ListAssignmentsForHITRequest
import com.amazonaws.services.mturk.model.ListReviewableHITsRequest

import com.typesafe.scalalogging.StrictLogging

case class SetNumHITsActive(value: Int)

object NumAssignmentsHITManager {

  def constAssignments[Prompt, Response](
    helper: HITManager.Helper[Prompt, Response],
    numAssignmentsPerPrompt: Int,
    initNumHITsToKeepActive: Int,
    _promptSource: Iterator[Prompt],
    shouldReviewPartiallyCompletedHITs: Boolean = true
  ) =
    new NumAssignmentsHITManager[Prompt, Response](
      helper,
      _ => numAssignmentsPerPrompt,
      initNumHITsToKeepActive,
      _promptSource,
      shouldReviewPartiallyCompletedHITs
    )
}

/** Simplest HIT manager, which gets a fixed number of assignments for every prompt
  * and approves all assignments immediately.
  * NOTE: this will crash if you try to have > 100 assignments per HIT.
  * could possibly be fixed but seems not necessary since that's a kind of ridic use case.
  */
class NumAssignmentsHITManager[Prompt, Response](
  helper: HITManager.Helper[Prompt, Response],
  numAssignmentsForPrompt: Prompt => Int,
  initNumHITsToKeepActive: Int,
  _promptSource: Iterator[Prompt],
  shouldReviewPartiallyCompletedHITs: Boolean = true
) extends HITManager[Prompt, Response](helper)
    with StrictLogging {

  var numHITsToKeepActive: Int = initNumHITsToKeepActive

  /** Override to add more possible incoming message types and message-processing logic. */
  def receiveAux2: PartialFunction[Any, Unit] =
    PartialFunction.empty[Any, Unit]

  override lazy val receiveAux: PartialFunction[Any, Unit] = ({
    case SetNumHITsActive(n) => numHITsToKeepActive = n
  }: PartialFunction[Any, Unit]) orElse receiveAux2

  import helper.config
  import helper.taskSpec.hitTypeId
  import helper.promptReader

  // override for more interesting review policy
  def reviewAssignment(hit: HIT[Prompt], assignment: Assignment[Response]): Unit = {
    helper.evaluateAssignment(hit, helper.startReviewing(assignment), Approval(""))
    if (!assignment.feedback.isEmpty) {
      logger.info(s"Feedback: ${assignment.feedback}")
    }
  }

  // override to do something interesting after a prompt finishes
  def promptFinished(prompt: Prompt): Unit = ()

  // override if you want fancier behavior
  override def addPrompt(prompt: Prompt): Unit = {
    queuedPrompts.enqueue(prompt)
  }

  val queuedPrompts = new LazyStackQueue[Prompt](_promptSource)

  def isFinished(prompt: Prompt) =
    helper.finishedHITInfos(prompt).map(_.assignments.size).sum >= numAssignmentsForPrompt(prompt)

  // upload new hits to fill gaps
  def refreshHITs = {
    val numToUpload = numHITsToKeepActive - helper.numActiveHITs
    for (_ <- 1 to numToUpload) {
      queuedPrompts.filterPop(p => !isFinished(p)) match {
        case None => () // we're finishing off, woo
        case Some(nextPrompt) =>
          if (helper.isActive(nextPrompt)) {
            // if this prompt is already active, queue it for later
            // TODO probably want to delay it by a constant factor instead
            queuedPrompts.enqueue(nextPrompt)
          } else {
            val numFinishedAssignments =
              helper.finishedHITInfos(nextPrompt).map(_.assignments.size).sum
            // following will be > 0 because isFinished was false
            val numRemainingAssignments = numAssignmentsForPrompt(nextPrompt) - numFinishedAssignments
            helper.createHIT(nextPrompt, numRemainingAssignments) recover {
              case _ => queuedPrompts.enqueue(nextPrompt) // put it back at the bottom to try later
            }
          }
      }
    }
  }

  import scala.collection.JavaConverters._

  final override def reviewHITs: Unit = {
    def reviewAssignmentsForHIT(hit: HIT[Prompt]) =
      for {
        getAssignmentsResult <- Try(
          config.service.listAssignmentsForHIT(
            (new ListAssignmentsForHITRequest)
              .withHITId(hit.hitId)
              .withMaxResults(numAssignmentsForPrompt(hit.prompt))
              .withAssignmentStatuses(AssignmentStatus.Submitted)
          )
        ).toOptionLogging(logger).toList
        mTurkAssignment <- getAssignmentsResult.getAssignments.asScala
        assignment = helper.taskSpec.makeAssignment(hit.hitId, mTurkAssignment)
        if !helper.isInReview(assignment)
      } yield {
        reviewAssignment(hit, assignment)
        assignment
      }

    // reviewable HITs; will always cover <= 100 HITs asking for only one assignment
    // it's rare that there are more than 100 reviewable HITs in one update,
    // and not the end of the world if we wait until the next one...
    // but... TODO fix with pagination
    val reviewableHITs = for {
      reviewableHITsResult <- Try(
        config.service.listReviewableHITs(
          (new ListReviewableHITsRequest)
            .withHITTypeId(hitTypeId)
            .withMaxResults(100)
        )
      ).toOptionLogging(logger).toList
      mTurkHIT <- reviewableHITsResult.getHITs.asScala
      hit <- config.hitDataService
        .getHIT[Prompt](hitTypeId, mTurkHIT.getHITId)
        .toOptionLogging(logger)
        .toList
    } yield {
      val assignmentSubmissions = reviewAssignmentsForHIT(hit)
      // if the HIT is "reviewable", and all its assignments are no longer "Submitted"
      // (in which case the above list would be empty), we can delete the HIT
      if (assignmentSubmissions.isEmpty) {
        helper.deleteHIT(hit)
        if (isFinished(hit.prompt)) {
          promptFinished(hit.prompt)
        }
      }
      hit
    }
    val reviewableHITSet = reviewableHITs.toSet

    // for HITs asking for more than one assignment, we want to check those manually
    if (shouldReviewPartiallyCompletedHITs) {
      for {
        (prompt, hitInfos) <- helper.activeHITInfosByPromptIterator.toList
        HITInfo(hit, _)    <- hitInfos
        if numAssignmentsForPrompt(hit.prompt) > 1 && !reviewableHITSet.contains(hit)
      } yield reviewAssignmentsForHIT(hit)
    }

    refreshHITs
  }
}
