package spacro

import scala.util.Try

import upickle.default._

import collection.mutable

// NOTE: not thread-safe
/** Simple in-memory implementation of HITDataService for use in testing and sample tasks. */
class InMemoryHITDataService extends HITDataService {

  case class HITStore(
    hit: HIT[String],
    approved: List[Assignment[String]],
    rejected: List[Assignment[String]]
  ) {
    def approve[Response : Writer](assignment: Assignment[Response]) =
      this.copy(approved = assignment.copy(response = write(assignment.response)) :: this.approved)
    def reject[Response : Writer](assignment: Assignment[Response]) =
      this.copy(rejected = assignment.copy(response = write(assignment.response)) :: this.rejected)
    def hitInfo = HITInfo(hit, approved)
  }
  object HITStore {
    def fromHIT[Prompt : Writer](hit: HIT[Prompt]) =
      HITStore(hit.copy(prompt = write(hit.prompt)), Nil, Nil)
  }

  private[this] val data = mutable.Map.empty[
    String, // hit type ID
    mutable.Map[
      String, // hit ID
      HITStore]]

  private[this] def getStoresForHITType(hitTypeId: String) = data.get(hitTypeId) match {
    case Some(infos) => infos
    case None =>
      val stores = mutable.Map.empty[String, HITStore]
      data.put(hitTypeId, stores)
      stores
  }

  private[this] def deserializeHIT[Prompt : Reader](hit: HIT[String]): HIT[Prompt] =
    hit.copy(prompt = read[Prompt](hit.prompt))

  private[this] def deserializeAssignment[Response : Reader](assignment: Assignment[String]): Assignment[Response] =
    assignment.copy(response = read[Response](assignment.response))

  private[this] def deserializeHITInfo[Prompt : Reader, Response : Reader](
    hi: HITInfo[String, String]
  ): HITInfo[Prompt, Response] = hi.copy(
    hit = deserializeHIT[Prompt](hi.hit),
    assignments = hi.assignments.map(deserializeAssignment[Response](_))
  )

  override def saveHIT[Prompt : Writer](
    hit: HIT[Prompt]
  ): Try[Unit] = Try {
    val hitStores = getStoresForHITType(hit.hitTypeId)
    hitStores.put(hit.hitId, HITStore.fromHIT(hit))
  }

  override def getHIT[Prompt : Reader](
    hitTypeId: String,
    hitId: String
  ): Try[HIT[Prompt]] = Try {
    deserializeHIT(getStoresForHITType(hitTypeId)(hitId).hit)
  }

  override def saveApprovedAssignment[Response : Writer](
    assignment: Assignment[Response]
  ): Try[Unit] = Try {
    val hitStores = getStoresForHITType(assignment.hitTypeId)
    val hitStore = hitStores(assignment.hitId)
    hitStores.put(assignment.hitId, hitStore.approve(assignment))
  }

  override def saveRejectedAssignment[Response : Writer](
    assignment: Assignment[Response]
  ): Try[Unit] = Try {
    val hitStores = getStoresForHITType(assignment.hitTypeId)
    val hitStore = hitStores(assignment.hitId)
    hitStores.put(assignment.hitId, hitStore.reject(assignment))
  }

  override def getHITInfo[Prompt: Reader, Response : Reader](
    hitTypeId: String,
    hitId: String
  ): Try[HITInfo[Prompt, Response]] = Try {
    deserializeHITInfo(getStoresForHITType(hitTypeId)(hitId).hitInfo)
  }

  override def getAllHITInfo[Prompt: Reader, Response : Reader](
    hitTypeId: String
  ): Try[List[HITInfo[Prompt, Response]]] = Try {
    getStoresForHITType(hitTypeId).values.toList
      .map(_.hitInfo)
      .map(deserializeHITInfo[Prompt, Response](_))
  }

  override def getAssignmentsForHIT[Response : Reader](
    hitTypeId: String,
    hitId: String
  ): Try[List[Assignment[Response]]] = Try {
    getStoresForHITType(hitTypeId)(hitId).approved.map(
      deserializeAssignment[Response](_)
    )
  }
}
