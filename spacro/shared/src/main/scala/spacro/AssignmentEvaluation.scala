package spacro

/** Represents an accept/reject decision for an assignment. */
sealed trait AssignmentEvaluation

/** Approval of an assignment.
  *
  * @param message the message shown with the approval (I think the worker can see this?)
  */
case class Approval(message: String) extends AssignmentEvaluation

/** Rejection of an assignment.
  *
  * The message parameter should be used to give an explanation of why the work was rejected.
  * I also like to pair it with advice on how to avoid another rejection.
  *
  * @param message the message shown to the worker with the rejection
  */
case class Rejection(message: String) extends AssignmentEvaluation
