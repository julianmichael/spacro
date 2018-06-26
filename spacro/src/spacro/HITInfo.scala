package spacro

/** Data structure to hold all data relevant to a particular HIT.
  * TODO: should include rejected assignments as well.
  */
case class HITInfo[Prompt, Response](hit: HIT[Prompt], assignments: List[Assignment[Response]])
