package in.rcard.sus4s

import java.util.concurrent.StructuredTaskScope.{ShutdownOnFailure, ShutdownOnSuccess}
import java.util.concurrent.{CompletableFuture, StructuredTaskScope}
import scala.concurrent.ExecutionException
import scala.concurrent.duration.Duration

object sus4s {

  /** Represents the capability to suspend the execution of a program. The suspension points are
    * those available for Java Virtual Threads.
    */
  trait Suspend {
    private[sus4s] val scope: StructuredTaskScope[Any]
  }

  // noinspection ScalaWeakerAccess
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

    /** Returns the value of the job. If the job is not completed, the method blocks until the job
      * completes. If the job is cancelled, the method throws an [[InterruptedException]].
      * @return
      *   The value of the job
      */
    def value: A =
      try cf.get()
      catch
        case exex: ExecutionException => throw exex.getCause
        case throwable: Throwable     => throw throwable

    /** Waits for the completion of the job. If the job is cancelled, no exception is thrown.
      */
    def join(): Unit =
      cf.handle((_, throwable) => {
        throwable match {
          case null                    => ()
          case _: InterruptedException => ()
          case _                       => throw throwable
        }
      })

    /** Cancels the job and all its children jobs. Getting the value of a cancelled job throws an
      * [[InterruptedException]]. Cancellation is an idempotent operation.
      *
      * <h2>Example</h2>
      * {{{
      *   val expectedQueue = structured {
      *   val queue = new ConcurrentLinkedQueue[String]()
      *   val job1 = fork {
      *     val innerJob = fork {
      *       fork {
      *         Thread.sleep(3000)
      *         println("inner-inner-Job")
      *         queue.add("inner-inner-Job")
      *       }
      *       Thread.sleep(2000)
      *       println("innerJob")
      *       queue.add("innerJob")
      *     }
      *     Thread.sleep(1000)
      *     queue.add("job1")
      *   }
      *   val job = fork {
      *     Thread.sleep(500)
      *     job1.cancel()
      *     queue.add("job2")
      *   }
      *   queue
      * }
      * expectedQueue.toArray should contain theSameElementsInOrderAs List("job2")
      * }}}
      */
    def cancel(): Suspend ?=> Unit = {
      // FIXME Refactor this code
      _cancel(executingThread.get(), summon[Suspend].asInstanceOf[SuspendScope].relationships)
      cf.completeExceptionally(new InterruptedException("Job cancelled"))
    }

    private def _cancel(
        thread: Thread,
        relationships: scala.collection.mutable.Map[Thread, List[Thread]]
    ): Unit = {
      relationships.get(thread) match {
        case Some(children) =>
          children.foreach(_cancel(_, relationships))
        case None => ()
      }
      thread.interrupt()
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
    val loomScope            = new ShutdownOnFailure()
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
      try {
        val resultValue = block
        result.complete(resultValue)
        resultValue
      } catch
        case _: InterruptedException =>
          result.completeExceptionally(new InterruptedException("Job cancelled"))
        case throwable: Throwable =>
          result.completeExceptionally(throwable)
          throw throwable;
    })
    Job(result, executingThread)
  }

  /** Suspends the execution of the current thread for the given duration.
    *
    * @param duration
    *   The duration to suspend the execution
    */
  def delay(duration: Duration): Suspend ?=> Unit = {
    Thread.sleep(duration.toMillis)
  }

  /** Races two concurrent tasks and returns the result of the first one that completes. The other
    * task is cancelled. If the first task throws an exception, it waits for the end of the second
    * task. If both tasks throw an exception, the first one is rethrown.
    *
    * Each block follows the [[structured]] concurrency model. So, for each block, a new virtual
    * thread is created more than the thread forking the block.
    *
    * <h2>Example</h2>
    * {{{
    * val results = new ConcurrentLinkedQueue[String]()
    * val actual: Int | String =
    *   race[Int, String](
    *     {
    *       delay(1.second)
    *       results.add("job1")
    *       throw new RuntimeException("Error")
    *     }, {
    *       delay(500.millis)
    *       results.add("job2")
    *       "42"
    *     }
    *   )
    * actual should be("42")
    * results.toArray should contain theSameElementsInOrderAs List("job2")
    * }}}
    *
    * @param firstBlock
    *   First block to race
    * @param secondBlock
    *   Second block to race
    * @tparam A
    *   Result type of the first block
    * @tparam B
    *   Result type of the second block
    * @return
    *   The result of the first block that completes
    */
  def race[A, B](firstBlock: Suspend ?=> A, secondBlock: Suspend ?=> B): A | B = {
    val loomScope            = new ShutdownOnSuccess[A | B]()
    given suspended: Suspend = SuspendScope(loomScope.asInstanceOf[StructuredTaskScope[Any]])
    try {
      loomScope.fork(() => { structured { firstBlock } })
      loomScope.fork(() => { structured { secondBlock } })

      loomScope.join()
      loomScope.result(identity)
    } finally {
      loomScope.close()
    }
  }
}
