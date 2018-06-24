package spacro.tasks

import scalajs.js
import org.scalajs.jquery.jQuery

import upickle.default._

/** Trait to be inherited by the main class for the JS client.
  * Dispatches to the appropriate task's client code using the defined
  * mapping from task keys to main methods.
  */
trait TaskDispatcher {

  // override this with the mapping from your task key to your task's main method
  def taskMapping: Map[String, () => Unit]

  import scala.scalajs.js.Dynamic.global

  lazy val taskKey: String = {
    read[String](jQuery(s"#${FieldLabels.taskKeyLabel}").attr("value").get)
  }

  final def main(args: Array[String]): Unit = jQuery { () =>
    // this needs to be done in order for the form submit to work
    global.turkSetAssignmentID()
    // dispatch to specific task
    taskMapping.get(taskKey) match {
      case None => System.err.println(s"Invalid task key: $taskKey")
      case Some(func) => func()
    }
  }
}
