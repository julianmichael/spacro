# spacro

Spacro (Single-PAge applications for CROwdsourcing) is a framework for doing crowdsourcing easily
in Scala. The use case is for complex crowdsourcing workflows with:

* Custom user interfaces, programmed in a single-page webapp (preferably with React), and
* multiple stages, where work performed by some workers needs to be re-uploaded in real time for
other workers to build on or validate.

If you require either of the above, this library could be useful to you. It will be especially
useful if you require both.

Projects using Spacro include:

* [Question-Answer Meaning Representation](https://github.com/uwnlp/qamr)
* [QA-SRL Crowdsourcing](https://github.com/julianmichael/qasrl-crowdsourcing)

## Getting Started

Spacro is built using [Mill](http://lihaoyi.com/mill).
I highly recommend using Mill to build client projects, because spacro is set up with the
assumption that the outputs of `fastOptJS` and `aggregatedJSDeps` are available in the JVM
`resources` (see [the sample build](spacro-sample/build.sc)). This is possible in other build
systems, but tried and true (and only takes a few lines) in Mill projects.

The fastest way to get started is to copy the
[sample project](spacro-sample), which should compile and run out of the box. You can then modify it to your needs. Both the library code and sample code have fairly detailed comments.

### Sample Quickstart

* Install [Mill](http://lihaoyi.com/mill).
* Modify [`spacro-sample/scripts/initSample.scala`](spacro-sample/scripts/initSample.scala), changing the definition of `val domain` to `localhost` or another hostname where your system can be reached.
* Execute [`spacro-sample/scripts/run_sample.sh`](spacro-sample/scripts/run_sample.sh). This will start the task server and drop you in a Scala REPL.
* In a separate terminal window, run `tail -f spacro-sample/sample.log`. This will show you if the server is listening for HTTP requests (though HTTPS configuration probably failed).
* Preview the annotation interface at `http://<domain>:7777/task/sample/preview`.

You should see a set of instructions and some task content (including a submit button, but this will not work since it's just a preview). If you see this, then you're ready to continue. When designing your project you can quickly iterate on the interface by accessing it through this preview.

To get the task up and running on MTurk for real (or sandbox) workers, there are several more steps. The best place these are documented is in the readme for
[the QAMR project code](https://github.com/uwnlp/qamr/tree/master/code)
(though it is slightly out of date, since it used SBT for the build).
I might have a more proper user guide at some point.
If you run into roadblocks, your best bet is
looking at/emulating example projects,
reading the source code (which is commented), 
or emailing me with your questions.
