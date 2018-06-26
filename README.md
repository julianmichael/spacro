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

The fastest way to get started is to copy the
[sample project](spacro-sample), which should compile and run out of the box. The library code
has some Scaladoc and should be fairly readable though.

Spacro is built using [Mill](http://lihaoyi.com/mill).
I highly recommend using Mill to build client projects, because spacro is set up with the
assumption that the outputs of `fastOptJS` and `aggregatedJSDeps` are available in the JVM
`resources` (see [the sample build](spacro-sample/build.sc)). This is possible in other build
systems, but tried and true (and only takes a few lines) in Mill projects.

There are several steps you need to take to get a task running on MTurk. The best place these are
documented is in the readme for
[the QAMR project code](https://github.com/uwnlp/qamr/tree/master/code)
(though it is slightly out of date).
A more proper user guide might come soon, but the spacro code will likely change as well.
If you run into roadblocks, your best bet is
looking at/emulating example projects,
reading the source code (it has Scaladoc), 
or, if all else fails, emailing me with your questions.
