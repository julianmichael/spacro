package spacro

/** Represents a single annotator's response to a HIT.
  *
  * Similar to the "Assignment" type from the MTurk API,
  * except that it only corresponds to a *finished* annotation,
  * which may have been accepted or rejected.
  * Also is well-typed for the purposes of an experiment,
  * parametrized over the type of Response you want.
  *
  * Another bit of post-processing: this accommodates for a "feedback"
  * field separate from the actual response, to make working with response data easier,
  * instead of having to embed the feedback in the response field.
  *
  * @tparam Response the desired data representation for annotators' responses
  * @param hitType the HIT type of the HIT this assignment was for
  * @param hitId the ID of the HIT this assignment was for
  * @param assignmentId the unique ID given to this assignment on MTurk
  * @param workerId the ID of the worker who did this assignment
  * @param acceptTime the time (millis from epoch) when the worker accepted the HIT
  * @param submitTime the time (millis from epoch) when the worker submitted the HIT
  * @param response the worker's response to the HIT
  * @param any feedback provided by the worker
  */
case class Assignment[Response](
  hitTypeId: String,
  hitId: String,
  assignmentId: String,
  workerId: String,
  acceptTime: Long,
  submitTime: Long,
  response: Response,
  feedback: String)

