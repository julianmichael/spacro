package spacro.tasks

import scalatags.Text.all._
import scalatags.Text.TypedTag

import io.circe.Encoder
import io.circe.syntax._

/** Contains the general HTML template for all tasks. */
object TaskPage {

  private[this] val printer = io.circe.Printer.noSpaces

  /** Constructs the HTML page for a given prompt and a given task. */
  def htmlPage[Prompt: Encoder](
    prompt: Prompt,
    taskSpec: TaskSpecification,
    useHttps: Boolean = true,
    headTags: List[TypedTag[String]],
    bodyEndTags: List[TypedTag[String]]
  )(implicit config: TaskConfig) = {
    val protocol =
      if (useHttps)
        "https:"
      else
        "http:"
    val port =
      if (useHttps)
        config.httpsPort
      else
        config.httpPort
    import config.serverDomain
    import config.projectName
    html(
      head(
        meta(
          name    := "viewport",
          content := "width=device-width, initial-scale=1, shrink-to-fit=no"
        ),
        script(
          `type` := "text/javascript",
          src    := "https://s3.amazonaws.com/mturk-public/externalHIT_v1.js"
        ),
        // script(
        //   `type` := "text/javascript",
        //   src := "https://cdnjs.cloudflare.com/ajax/libs/react/15.6.1/react.js"
        // ),
        // script(
        //   `type` := "text/javascript",
        //   src := "https://cdnjs.cloudflare.com/ajax/libs/react/15.6.1/react-dom.js"
        // ),
        // script(
        //   `type` := "text/javascript",
        //   src := s"https://code.jquery.com/jquery-2.1.4.min.js"),
        // script(
        //   `type` := "text/javascript",
        //   src := s"$protocol//$serverDomain:$port/$projectName-jsdeps.js"),
        // script(
        //   `type` := "text/javascript",
        //   src := s"$protocol//$serverDomain:$port/$projectName-fastopt.js"),
        // script(
        //   `type` := "text/javascript",
        //   src := s"$protocol//$serverDomain:$port/$projectName-launcher.js"),
        headTags
      ),
      body()(
        input(
          `type` := "hidden",
          value  := printer.print(prompt.asJson),
          name   := FieldLabels.promptLabel,
          id     := FieldLabels.promptLabel
        ),
        input(
          `type` := "hidden",
          value  := printer.print(serverDomain.asJson),
          name   := FieldLabels.serverDomainLabel,
          id     := FieldLabels.serverDomainLabel
        ),
        input(
          `type` := "hidden",
          value  := printer.print(config.httpPort.asJson),
          name   := FieldLabels.httpPortLabel,
          id     := FieldLabels.httpPortLabel
        ),
        input(
          `type` := "hidden",
          value  := printer.print(config.httpsPort.asJson),
          name   := FieldLabels.httpsPortLabel,
          id     := FieldLabels.httpsPortLabel
        ),
        input(
          `type` := "hidden",
          value  := printer.print(taskSpec.taskKey.asJson),
          name   := FieldLabels.taskKeyLabel,
          id     := FieldLabels.taskKeyLabel
        ),
        form(
          name   := FieldLabels.mturkFormLabel,
          method := "post",
          id     := FieldLabels.mturkFormLabel,
          action := config.externalSubmitURL
        )(
          // where turk puts the assignment ID
          input(`type` := "hidden", value := "", name := "assignmentId", id := "assignmentId"),
          // where our client code should put the response
          input(
            `type` := "hidden",
            value  := "", // TODO printer.print((None: Option[Response]).asJson)
            name   := FieldLabels.responseLabel,
            id     := FieldLabels.responseLabel
          ),
          // and here I'll let the client code do its magic
          div(
            id := FieldLabels.rootClientDivLabel,
            "Waiting for task data from server... (If this message does not disappear shortly, the server is down. Try refreshing in a minute or so.)"
          )
        ),
        bodyEndTags,
        script(`type` := "text/javascript", src := s"$protocol//$serverDomain:$port/jsdeps.js"),
        script(`type` := "text/javascript", src := s"$protocol//$serverDomain:$port/out.js")
      )
    )
  }
}
