package spacro.sample

import spacro.tasks._

import scalajs.js.JSApp

/** Main class for the client; dispatches to the sample task. */
object Dispatcher extends TaskDispatcher with JSApp {

  override val taskMapping = Map[String, () => Unit](
    sampleTaskKey -> (() => Client.main())
  )
}
