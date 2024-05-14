package in.rcard.sus4s

import java.util.concurrent.{CompletableFuture, StructuredTaskScope}

object sus4s {
  trait Suspend {
    val scope: StructuredTaskScope[Any]
  }

  type Suspended[A] = Suspend ?=> A

  class Job[A] private[sus4s] (private val cf: CompletableFuture[A]) {
    def value: A = cf.join()
  }

  inline def structured[A](inline block: Suspend ?=> A): A = {
    val _scope = new StructuredTaskScope.ShutdownOnFailure()

    given suspended: Suspend = new Suspend {
      override val scope: StructuredTaskScope[Any] = _scope
    }

    try {
      val mainTask = _scope.fork(() => {
        block
      })
      _scope.join().throwIfFailed(identity)
      mainTask.get()
    } finally {
      _scope.close()
    }
  }

  def fork[A](block: Suspend ?=> A): Suspend ?=> Job[A] = {
    val result = new CompletableFuture[A]()
    summon[Suspend].scope.fork(() => {
      try result.complete(block)
      catch
        case throwable: Throwable =>
          result.completeExceptionally(throwable)
          throw throwable;
    })
    Job(result)
  }
}
