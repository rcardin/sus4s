package in.rcard.sus4s

import java.util.concurrent.StructuredTaskScope.ShutdownOnFailure
import java.util.concurrent.{CompletableFuture, StructuredTaskScope}

object sus4s {

  /** Represents the capability to suspend the execution of a program. The suspension points are
    * those available for Java Virtual Threads.
    */
  trait Suspend {
    private[sus4s] val scope: StructuredTaskScope[Any]
  }

  final class SuspendScope(override private[sus4s] val scope: StructuredTaskScope[Any])
      extends Suspend {
    private[sus4s] val relationships = scala.collection.mutable.Map.empty[Thread, List[Thread]]
  }

  /** A value of type `A` obtained by a function that can suspend */
  type Suspended[A] = Suspend ?=> A

  /** Represents a concurrent job returning a value of type `A`.
    * @param cf
    *   Contains the result of the job
    * @tparam A
    *   The type of the value returned by the job
    */
  class Job[A] private[sus4s] (
      private val cf: CompletableFuture[A],
      private val executingThread: CompletableFuture[Thread]
  ) {
    def value: A = cf.get()
    def join(): Unit =
      cf.handle((_, throwable) => {
        throwable match {
          case null                    => ()
          case _: InterruptedException => ()
          case _                       => throw throwable
        }
      })
    def cancel(): Suspend ?=> Unit = {
      summon[Suspend].asInstanceOf[SuspendScope].relationships.get(executingThread.get()) match {
        case Some(children) =>
          children.foreach(_.interrupt())
        case None => ()
      }
      executingThread.get().interrupt()
      cf.completeExceptionally(new InterruptedException("Job cancelled"))
    }
  }

  /** Executes a block of code applying structured concurrency to the contained suspendable tasks
    * and returns the result of the concurrent computation. In detail, a new concurrent task can be
    * forked using the [[fork]] function. The structured concurrency ensures that the thread
    * executing the block waits for the completion of all the forked tasks.
    *
    * The `structured` blocks terminates when:
    *   - all the forked tasks complete successfully
    *   - one of the forked tasks throws an exception
    *   - the block throws an exception
    *
    * The `structured` function is completely transparent to any exception thrown by the block or by
    * any of the forked tasks.
    *
    * <h2>Example</h2>
    * {{{
    * val result = structured {
    *   val job1 = fork {
    *     Thread.sleep(1000)
    *     queue.add("job1")
    *     42
    *   }
    *   val job2 = fork {
    *     Thread.sleep(500)
    *     queue.add("job2")
    *     43
    *   }
    *   job1.value + job2.value
    * }
    * result shouldBe 85
    * }}}
    *
    * Structured concurrency is implemented on top of Java Virtual Threads.
    *
    * @param block
    *   The block of code to execute in a structured concurrency context
    * @tparam A
    *   The type of the result of the block
    * @return
    *   The result of the block
    */
  inline def structured[A](inline block: Suspend ?=> A): A = {
    val loomScope = new ShutdownOnFailure()
    given suspended: Suspend = SuspendScope(loomScope)

    try {
      val mainTask = loomScope.fork(() => {
        block
      })
      loomScope.join().throwIfFailed(identity)
      mainTask.get()
    } finally {
      loomScope.close()
    }
  }

  /** Forks a new concurrent task executing the given block of code and returning a [[Job]] that
    * completes with the value of type `A`. The task is executed in a new Virtual Thread using the
    * given [[Suspend]] context. The job is transparent to any exception thrown by the `block`,
    * which means it rethrows the exception.
    *
    * <h2>Example</h2>
    * {{{
    * structured {
    *   val job1: Job[Int] = fork {
    *     Thread.sleep(1000)
    *     queue.add("job1")
    *     42
    *   }
    * }
    * }}}
    *
    * @param block
    *   The block of code to execute concurrently
    * @tparam A
    *   The type of the value returned by the block
    * @return
    *   A [[Job]] representing the concurrent task
    *
    * @see
    *   [[structured]]
    */
  def fork[A](block: Suspend ?=> A): Suspend ?=> Job[A] = {
    val result                     = new CompletableFuture[A]()
    val executingThread            = new CompletableFuture[Thread]()
    val suspendScope: SuspendScope = summon[Suspend].asInstanceOf[SuspendScope]
    val parentThread: Thread       = Thread.currentThread()
    suspendScope.scope.fork(() => {
      val childThread = Thread.currentThread()
      suspendScope.relationships.updateWith(parentThread) {
        case None    => Some(List(childThread))
        case Some(l) => Some(childThread :: l)
      }
      executingThread.complete(childThread)
      try result.complete(block)
      catch
        case _: InterruptedException =>
          result.completeExceptionally(new InterruptedException("Job cancelled"))
        case throwable: Throwable =>
          result.completeExceptionally(throwable)
          throw throwable;
    })
    Job(result, executingThread)
  }
}
