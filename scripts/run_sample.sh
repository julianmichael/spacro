#!/bin/bash

{ echo ":load scripts/initSample.scala" & cat <&0; } | sbt "project spacroSampleJVM" console

