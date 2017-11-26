package spacro.util

import scala.collection.mutable

/**
  * A mutable data structure based on the following operations:
  *   push: adds to the top
  *   pop: takes from the top
  *   enqueue: adds to the bottom
  * The other main feature is that you instantiate it with an Iterator of starting items,
  * which it only traverses as necessary, buffering up to maxBufferSize elements at a time.
  * Note that items enqueued into the LazyStackQueue will only be popped off after the source iterator is empty.
  * LazyStackQueue should be viewed as "consuming" the iterator---you should not
  * use the iterator again after passing it into the constructor.
  *
  * @constructor makes a LazyStackQueue
  * @param source the initial elements of the queue to be lazily loaded as demanded
  * @param maxBufferSize the max number of elements to buffer from the source
  */
class LazyStackQueue[A](
  private[this] val source: Iterator[A],
  private[this] val maxBufferSize: Int = 10
) {

  /** Returns the total number of elements in the component queues,
    * not counting those buffered from the source iterator.
    */
  def numManuallyEnqueued: Int = top.size + bottom.size

  // used to store anything pushed on
  private[this] val top: mutable.Stack[A] = mutable.Stack.empty[A]

  // a buffer for the source iterator
  private[this] val middle: mutable.Queue[A] = mutable.Queue.empty[A]

  // used to store anything enqueued
  private[this] val bottom: mutable.Queue[A] = mutable.Queue.empty[A]

  /** Pushes an element on top. */
  def push(a: A): Unit = top.push(a)

  /** Enqueues an element on bottom. */
  def enqueue(a: A): Unit = bottom.enqueue(a)

  /** Pops an element from the top. */
  def pop: Option[A] =
    top.popOption.orElse(popFromMiddleOption).orElse(bottom.dequeueOption)

  /** Pops until finding an element satisfying the given predicate, and returns it (or None if exhausted). */
  def filterPop(predicate: A => Boolean): Option[A] =
    Stream.continually(pop).dropWhile(!_.forall(predicate)).head

  /** Pops n elements from the top (or fewer if exhausted). */
  def pop(n: Int): List[A] =
    Vector.fill(n)(pop).flatten.toList

  /** Pops until having obtained n elements satisfying the predicate (or fewer if exhausted). */
  def filterPop(predicate: A => Boolean, n: Int): List[A] =
    Vector.fill(n)(filterPop(predicate)).flatten.toList

  // auxiliary method for managing the middle buffer
  private[this] def popFromMiddleOption: Option[A] = if(!middle.isEmpty) {
    Some(middle.dequeue)
  } else if(source.hasNext) {
    val result = source.next
    middle.enqueue(source.take(maxBufferSize).toSeq:_*)
    Some(result)
  } else {
    None
  }
}
