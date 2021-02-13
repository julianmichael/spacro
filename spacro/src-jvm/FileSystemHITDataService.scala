package spacro

import spacro.util._

import java.nio.file.{Files, Path, Paths}

import scala.util.Try
import scala.concurrent.duration._
import scala.language.postfixOps

import resource.managed
import resource.ManagedResource

import com.typesafe.scalalogging.StrictLogging

import io.circe.{Encoder, Decoder}
import io.circe.syntax._

/** Implementation of HITDataService against the file system.
  * Works for most purposes; would be better to have an implementation against a database
  * for a really big project.
  */
class FileSystemHITDataService(root: Path) extends HITDataService with StrictLogging {

  private[this] val printer = io.circe.Printer.noSpaces
  import io.circe.parser._

  // == Basic auxiliary methods ==

  private[this] def loadFile(path: Path): ManagedResource[Iterator[String]] = {
    import scala.collection.JavaConverters._
    managed(Files.lines(path)).map(_.iterator.asScala)
  }

  private[this] def loadSerialized[A: Decoder](path: Path): Try[A] = {
    val res = for {
      lines <- loadFile(path)
    } yield Try(decode[A](lines.mkString("\n")).right.get)
    res.tried.flatten
  }

  private[this] def saveFile(path: Path, contents: String): Try[Unit] =
    Try(Files.write(path, contents.getBytes))

  // == Useful file/folder names ==

  private[this] val hitFilename = "hit.txt"
  private[this] val rejectionDirectory = "rejections"

  // == Path accessors ==

  // Convenience methods to get file paths and create missing directories if necessary.

  private[this] def getRootPath: Path = {
    if (!Files.exists(root)) {
      Files.createDirectories(root);
    }
    root
  }

  private[this] def getHITTypePath(hitTypeId: String): Path = {
    val hitTypePath = getRootPath.resolve(hitTypeId)
    if (!Files.exists(hitTypePath)) {
      Files.createDirectories(hitTypePath);
    }
    hitTypePath
  }

  // TODO maybe this should check for existence of hit.txt?
  private[this] def hitExists(hitTypeId: String, hitId: String) = {
    Files.exists(getHITTypePath(hitTypeId).resolve(hitId))
  }

  private[this] def getHITPath(hitTypeId: String, hitId: String) = {
    val hitPath = getHITTypePath(hitTypeId).resolve(hitId)
    if (!Files.exists(hitPath)) {
      Files.createDirectories(hitPath);
    }
    hitPath
  }

  private[this] def getRejectionPath(hitTypeId: String, hitId: String) = {
    val hitPath = getHITPath(hitTypeId, hitId)
    val rejectionPath = hitPath.resolve(rejectionDirectory)
    if (!Files.exists(rejectionPath)) {
      Files.createDirectories(rejectionPath);
    }
    rejectionPath
  }

  // == Saving / loading specific kinds of files ==

  override def saveHIT[Prompt: Encoder](hit: HIT[Prompt]): Try[Unit] = {
    val savePath = getHITPath(hit.hitTypeId, hit.hitId).resolve(hitFilename)
    saveFile(savePath, printer.print(hit.asJson))
  }

  import com.softwaremill.macmemo.memoize
  import com.softwaremill.macmemo.MemoCacheBuilder
  private[this] implicit val cacheProvider = MemoCacheBuilder.guavaMemoCacheBuilder

  @memoize(maxSize = 500, expiresAfter = 12 hours)
  private[this] def loadHITUnsafe[Prompt: Decoder](path: Path): HIT[Prompt] =
    loadSerialized[HIT[Prompt]](path.resolve(hitFilename)).get

  override def getHIT[Prompt: Decoder](hitTypeId: String, hitId: String): Try[HIT[Prompt]] =
    if (hitExists(hitTypeId, hitId)) Try(loadHITUnsafe(getHITPath(hitTypeId, hitId)))
    else scala.util.Failure(new RuntimeException(s"HIT ($hitTypeId; $hitId) does not exist"))

  override def saveApprovedAssignment[Response: Encoder](
    assignment: Assignment[Response]
  ): Try[Unit] = Try {
    val directory = getHITPath(assignment.hitTypeId, assignment.hitId)
    val savePath = directory.resolve(s"${assignment.assignmentId}.txt")
    saveFile(savePath, printer.print(assignment.asJson))
  }

  override def saveRejectedAssignment[Response: Encoder](
    assignment: Assignment[Response]
  ): Try[Unit] = Try {
    val directory = getRejectionPath(assignment.hitTypeId, assignment.hitId)
    val savePath = directory.resolve(s"${assignment.assignmentId}.txt")
    saveFile(savePath, printer.print(assignment.asJson))
  }

  override def getHITInfo[Prompt: Decoder, Response: Decoder](
    hitTypeId: String,
    hitId: String
  ): Try[HITInfo[Prompt, Response]] =
    for {
      hit         <- getHIT[Prompt](hitTypeId, hitId)
      assignments <- getAssignmentsForHIT[Response](hitTypeId, hitId)
    } yield HITInfo(hit, assignments)

  override def getAllHITInfo[Prompt: Decoder, Response: Decoder](
    hitTypeId: String
  ): Try[List[HITInfo[Prompt, Response]]] = Try {
    val allData = for {
      hitFolder <- new java.io.File(getHITTypePath(hitTypeId).toString).listFiles
      if hitFolder.isDirectory // exclude extraneous files if necessary --- shouldn't happen though
      hit <- Try(loadHITUnsafe[Prompt](Paths.get(hitFolder.getPath))).toOptionLogging(logger).toList
      assignments <- getAssignmentsForHIT[Response](hit.hitTypeId, hit.hitId)
        .toOptionLogging(logger)
        .toList
    } yield HITInfo(hit, assignments)
    allData.toList
  }

  override def getAssignmentsForHIT[Response: Decoder](
    hitTypeId: String,
    hitId: String
  ): Try[List[Assignment[Response]]] = Try {
    val hitPath = getHITPath(hitTypeId, hitId)
    val assignments = for {
      file <- new java.io.File(hitPath.toString).listFiles
      if !file.isDirectory // exclude rejection directory
      if !file.getPath.toString.endsWith(hitFilename.toString) // exclude hit.txt
      assignment <- loadSerialized[Assignment[Response]](Paths.get(file.getPath))
        .toOptionLogging(logger)
        .toList
    } yield assignment
    assignments.toList
  }
}
