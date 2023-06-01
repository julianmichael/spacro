package spacro

import scala.util.Try

import io.circe.{Encoder, Decoder}
import io.circe.syntax._

import collection.mutable

// NOTE: not thread-safe
/** Simple in-memory implementation of HITDataService for use in testing and sample tasks. */
class InMemoryHITDataService extends HITDataService {

  private[this] val printer = io.circe.Printer.noSpaces
  import io.circe.parser._

  case class HITStore(
    hit: HIT[String],
    approved: List[Assignment[String]],
    rejected: List[Assignment[String]]
  ) {

    def approve[Response: Encoder](assignment: Assignment[Response]) = this.copy(approved =
      assignment.copy(response = printer.print(assignment.response.asJson)) :: this.approved
    )

    def reject[Response: Encoder](assignment: Assignment[Response]) = this.copy(rejected =
      assignment.copy(response = printer.print(assignment.response.asJson)) :: this.rejected
    )
    def hitInfo = HITInfo(hit, approved)
  }

  object HITStore {

    def fromHIT[Prompt: Encoder](hit: HIT[Prompt]) = HITStore(
      hit.copy(prompt = printer.print(hit.prompt.asJson)),
      Nil,
      Nil
    )
  }

  private[this] val data = mutable
    .Map
    .empty[
      String, // hit type ID
      mutable.Map[
        String, // hit ID
        HITStore
      ]
    ]

  private[this] def getStoresForHITType(hitTypeId: String) =
    data.get(hitTypeId) match {
      case Some(infos) =>
        infos
      case None =>
        val stores = mutable.Map.empty[String, HITStore]
        data.put(hitTypeId, stores)
        stores
    }

  private[this] def deserializeHIT[Prompt: Decoder](hit: HIT[String]): HIT[Prompt] = hit
    .copy(prompt = decode[Prompt](hit.prompt).right.get)

  private[this] def deserializeAssignment[Response: Decoder](
    assignment: Assignment[String]
  ): Assignment[Response] = assignment
    .copy(response = decode[Response](assignment.response).right.get)

  private[this] def deserializeHITInfo[Prompt: Decoder, Response: Decoder](
    hi: HITInfo[String, String]
  ): HITInfo[Prompt, Response] = hi.copy(
    hit = deserializeHIT[Prompt](hi.hit),
    assignments = hi.assignments.map(deserializeAssignment[Response](_))
  )

  override def saveHIT[Prompt: Encoder](hit: HIT[Prompt]): Try[Unit] = Try {
    val hitStores = getStoresForHITType(hit.hitTypeId)
    hitStores.put(hit.hitId, HITStore.fromHIT(hit))
  }

  override def getHIT[Prompt: Decoder](hitTypeId: String, hitId: String): Try[HIT[Prompt]] = Try {
    deserializeHIT(getStoresForHITType(hitTypeId)(hitId).hit)
  }

  override def saveApprovedAssignment[Response: Encoder](
    assignment: Assignment[Response]
  ): Try[Unit] = Try {
    val hitStores = getStoresForHITType(assignment.hitTypeId)
    val hitStore  = hitStores(assignment.hitId)
    hitStores.put(assignment.hitId, hitStore.approve(assignment))
  }

  override def saveRejectedAssignment[Response: Encoder](
    assignment: Assignment[Response]
  ): Try[Unit] = Try {
    val hitStores = getStoresForHITType(assignment.hitTypeId)
    val hitStore  = hitStores(assignment.hitId)
    hitStores.put(assignment.hitId, hitStore.reject(assignment))
  }

  override def getHITInfo[Prompt: Decoder, Response: Decoder](
    hitTypeId: String,
    hitId: String
  ): Try[HITInfo[Prompt, Response]] = Try {
    deserializeHITInfo(getStoresForHITType(hitTypeId)(hitId).hitInfo)
  }

  override def getAllHITInfo[Prompt: Decoder, Response: Decoder](
    hitTypeId: String
  ): Try[List[HITInfo[Prompt, Response]]] = Try {
    getStoresForHITType(hitTypeId)
      .values
      .toList
      .map(_.hitInfo)
      .map(deserializeHITInfo[Prompt, Response](_))
  }

  override def getAssignmentsForHIT[Response: Decoder](
    hitTypeId: String,
    hitId: String
  ): Try[List[Assignment[Response]]] = Try {
    getStoresForHITType(hitTypeId)(hitId).approved.map(deserializeAssignment[Response](_))
  }
}
