package spacro.util

sealed trait Span {
  def begin: Int
  def end: Int
  def length = end - begin + 1
  def contains(i: Int) = begin <= i && i <= end
}

case class SpanImpl(
  override val begin: Int,
  override val end: Int) extends Span {
  override def toString = s"Span($begin, $end)"
}

object Span {
  import upickle.default._
  implicit val contiguousSpanReadWriter: ReadWriter[Span] =
    macroRW[SpanImpl] merge[SpanImpl, Span] macroRW[SpanImpl]
  // spurious extra case just to get the types to work out ^

  def apply(x: Int, y: Int): Span = SpanImpl(math.min(x, y), math.max(x, y))
  def unapply(cs: Span): Option[(Int, Int)] = SpanImpl.unapply(cs.asInstanceOf[SpanImpl])
}

