package spacro

import scala.util.{Failure, Success, Try}

import scala.collection.mutable
import scala.collection.TraversableOnce

import scala.language.implicitConversions

import com.typesafe.scalalogging.Logger

import java.io.StringWriter
import java.io.PrintWriter

import com.amazonaws.services.mturk.AmazonMTurk
import com.amazonaws.services.mturk.model.{HIT => MTurkHIT}
import com.amazonaws.services.mturk.model.QualificationType
import com.amazonaws.services.mturk.model.ListHITsRequest
import com.amazonaws.services.mturk.model.ListWorkersWithQualificationTypeRequest
import com.amazonaws.services.mturk.model.ListQualificationTypesRequest

/** Utility classes, methods, and extension methods for spacro. */
package object util {
  // this is basically what we want to do with most errors
  implicit protected[spacro] class RichTry[A](val t: Try[A]) extends AnyVal {

    def toOptionLogging(logger: Logger): Option[A] =
      t match {
        case Success(a) =>
          Some(a)
        case Failure(e) =>
          val sw = new StringWriter()
          val pw = new PrintWriter(sw, true)
          e.printStackTrace(pw)
          logger.error(e.getLocalizedMessage + "\n" + sw.getBuffer.toString)
          None
      }
  }

  // the two ext. methods are mainly nice for the LazyStackQueue implementation.
  // also they should seriously already exist...

  implicit protected[spacro] class RichMutableStack[A](val s: mutable.Stack[A]) extends AnyVal {
    def popOption: Option[A] =
      if (!s.isEmpty)
        Some(s.pop)
      else
        None
  }

  implicit protected[spacro] class RichMutableQueue[A](val q: mutable.Queue[A]) extends AnyVal {
    def dequeueOption: Option[A] =
      if (!q.isEmpty)
        Some(q.dequeue)
      else
        None
  }

  // convenience methods for mturk
  implicit class RichAmazonMTurk(val client: AmazonMTurk) extends AnyVal {

    import scala.collection.JavaConverters._
    import scala.annotation.tailrec

    def listAllHITs = {
      @tailrec
      def getAllHITsAux(hitsSoFar: Vector[MTurkHIT], request: ListHITsRequest): Vector[MTurkHIT] = {
        val nextPage             = client.listHITs(request)
        val (newHITs, nextToken) = (nextPage.getHITs, nextPage.getNextToken)
        if (newHITs == null) {
          hitsSoFar
        } else {
          val aggHITs = hitsSoFar ++ nextPage.getHITs.asScala
          if (nextToken == null) {
            aggHITs
          } else {
            getAllHITsAux(aggHITs, request.withNextToken(nextToken))
          }
        }
      }
      getAllHITsAux(Vector.empty[MTurkHIT], new ListHITsRequest().withMaxResults(100))
    }

    def listAllWorkersWithQualificationType(qualTypeId: String): Vector[String] = {
      @tailrec
      def getAllWorkersWithQualTypeAux(
        workersSoFar: Vector[String],
        request: ListWorkersWithQualificationTypeRequest
      ): Vector[String] = {
        val nextPage                        = client.listWorkersWithQualificationType(request)
        val (nextQualifications, nextToken) = (nextPage.getQualifications, nextPage.getNextToken)
        if (nextQualifications == null) {
          workersSoFar
        } else {
          val aggWorkers = workersSoFar ++ nextQualifications.asScala.toVector.map(_.getWorkerId)
          if (nextToken == null) {
            aggWorkers
          } else {
            getAllWorkersWithQualTypeAux(aggWorkers, request.withNextToken(nextToken))
          }
        }
      }
      getAllWorkersWithQualTypeAux(
        Vector.empty[String],
        new ListWorkersWithQualificationTypeRequest()
          .withQualificationTypeId(qualTypeId)
          .withMaxResults(100)
      )
    }

    def listAllQualificationTypes(request: ListQualificationTypesRequest) = {
      @tailrec
      def listAllQualificationTypesAux(
        qualTypesSoFar: Vector[QualificationType],
        req: ListQualificationTypesRequest
      ): Vector[QualificationType] = {
        val nextPage                   = client.listQualificationTypes(request)
        val (nextQualTypes, nextToken) = (nextPage.getQualificationTypes, nextPage.getNextToken)
        if (nextQualTypes == null) {
          qualTypesSoFar
        } else {
          val aggQualTypes = qualTypesSoFar ++ nextQualTypes.asScala.toVector
          if (nextToken == null) {
            aggQualTypes
          } else {
            listAllQualificationTypesAux(aggQualTypes, req.withNextToken(nextToken))
          }
        }
      }
      listAllQualificationTypesAux(Vector.empty[QualificationType], request)
    }
  }

}
