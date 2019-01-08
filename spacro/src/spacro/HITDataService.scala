package spacro

import scala.util.Try

import io.circe.{Encoder, Decoder}

/**
  * API for services that store the data returned by workers across HITs on MTurk.
  * Semantics are append-only; you can save HITs and Assignments and get them later,
  * but cannot delete any data.
  *
  * Not expected to be thread-safe in general.
  * TODO: implement this using the interpreter pattern instead.
  */
trait HITDataService {

  /** Save a HIT that has been uploaded to MTurk.
    * This should happen directly after successful HIT creation.
    */
  def saveHIT[Prompt: Encoder](
    hit: HIT[Prompt]
  ): Try[Unit]

  /** Get a stored HIT by its HIT Type ID and HIT ID, which together uniquely identify it.
    * (HIT ID may already be unique; I'm not sure.)
    */
  def getHIT[Prompt: Decoder](
    hitTypeId: String,
    hitId: String
  ): Try[HIT[Prompt]]

  /** Save the data of an assignment that has been approved on MTurk.
    * Should happen directly after the assignment is approved.
    */
  def saveApprovedAssignment[Response: Encoder](
    assignment: Assignment[Response]
  ): Try[Unit]

  /** Save the data of an assignment that has been rejected on MTurk.
    * Should happen directly after the assignment is rejected.
    */
  def saveRejectedAssignment[Response: Encoder](
    assignment: Assignment[Response]
  ): Try[Unit]

  /** Get a saved HIT and all data relevant to that HIT. */
  def getHITInfo[Prompt: Decoder, Response: Decoder](
    hitTypeId: String,
    hitId: String
  ): Try[HITInfo[Prompt, Response]]

  /** Get all saved HIT data for a given HIT Type. */
  def getAllHITInfo[Prompt: Decoder, Response: Decoder](
    hitTypeId: String
  ): Try[List[HITInfo[Prompt, Response]]]

  // TODO implement the below in terms of getHITInfo
  /** Get all assignments for a given HIT. */
  def getAssignmentsForHIT[Response: Decoder](
    hitTypeId: String,
    hitId: String
  ): Try[List[Assignment[Response]]]

}
