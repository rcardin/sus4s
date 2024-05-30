![GitHub Workflow Status (with branch)](https://img.shields.io/github/actions/workflow/status/rcardin/sus4s/scala.yml?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/in.rcard.sus4s/core_3)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/rcardin/sus4s)
[![javadoc](https://javadoc.io/badge2/in.rcard.sus4s/core_3/javadoc.svg)](https://javadoc.io/doc/in.rcard.sus4s/core_3)

# sus4s 🎸🎶

A Direct-Style Scala Wrapper Around the Structured Concurrency of Project Loom

## Dependency

The library is available on Maven Central. To use it, add the following dependency to your `build.sbt` files:

```sbt
libraryDependencies += "in.rcard.sus4s" %% "core" % "0.0.2"
```

The library is only available for Scala 3.

## Usage

The library provides a direct-style API for Project Loom's structured concurrency. It requires JDK 21 and Scala 3. Moreover, Java preview features must be enabled in the Scala compiler.

The main entry point is the `sus4s` package object. The following code snippet shows how to use the library:

```scala 3
import in.rcard.sus4s.sus4s.*

val result: Int = structured {
  val job1: Job[Int] = fork {
    Thread.sleep(1000)
    42
  }
  val job2: Job[Int] = fork {
    Thread.sleep(500)
    43
  }
  job1.value + job2.value
}
println(result) // 85
```

The `structured` method creates a new structured concurrency scope represented by the `Suspend` trait. It's built on the `java.util.concurrent.StructuredTaskScope` class. Hence, the threads forked inside the `structured` block are Java Virtual Threads.

The `fork` method creates a new Java Virtual Thread that executes the given code block. The `fork` method executes functions declared with the capability of suspend:

```scala 3
def findUserById(id: UserId): Suspend ?=> User
```

Coloring a function with the `Suspend` capability tells the caller that the function performs a suspendable operation, aka some effect. Suspension is managed by the Loom runtime, which is responsible for scheduling the virtual threads.

A type alias is available for the `Suspend` capability:

```scala 3
type Suspended[A] = Suspend ?=> A
```

So, the above function can be rewritten as:

```scala 3
def findUserById(id: UserId): Suspended[User]
```

The `structured` function uses structured concurrency to run the suspendable tasks. In detail, it ensures that the thread executing the block waits to complete all the forked tasks. The structured blocks terminate when:

- all the forked tasks complete successfully
- one of the forked tasks throws an exception
- the block throws an exception

## The `Job` Class

Forking a suspendable function means creating a new virtual thread that executes the function. The thread is represented by the `Job` class. The `Job` class provides the `value` method that waits for the completion of the virtual thread and returns the result of the function:

```scala 3
val job1: Job[Int] = fork {
  Thread.sleep(1000)
  42
}
val meaningOfLife: Int = job1.value
```

If you're not interested in the result of the function, you can use the `join` method:

```scala 3
val job1: Job[Int] = fork {
  Thread.sleep(1000)
  println("The meaning of life is 42")
}
job1.join()
```

The `structured` function is entirely transparent to any exception thrown by the block or forked tasks.

## Canceling a Job

Canceling a job is possible by calling the `cancel` method on the `Job` instance. The following code snippet shows how:

```scala 3
val queue = new ConcurrentLinkedQueue[String]()
val result = structured {
  val job1 = fork {
    val innerCancellableJob = fork {
      while (true) {
        Thread.sleep(2000)
        queue.add("cancellable")
      }
    }
    Thread.sleep(1000)
    innerCancellableJob.cancel()
    queue.add("job1")
  }
  val job = fork {
    Thread.sleep(500)
    queue.add("job2")
    43
  }
  job.value
}
queue.toArray should contain theSameElementsInOrderAs List("job2", "job1")
result shouldBe 43
```

Cancellation is collaborative. In the above example, the job `innerCancellableJob` is marked for cancellation by the call `innerCancellableJob.cancel()`. However, the job is not immediately canceled. The job is canceled when it reaches the first point operation that can be _interrupted_ by the JVM. Hence, cancellation is based on the concept of interruption. In the above example, the `innerCancellableJob` is canceled when it reaches the `Thread.sleep(2000)` operation. The job will never be canceled if we remove the `Thread.sleep` operation. A similar behavior is implemented by Kotlin coroutines (see [Kotlin Coroutines - A Comprehensive Introduction / Cancellation](https://blog.rockthejvm.com/kotlin-coroutines-101/#7-cancellation) for further details).

Cancelling a job follows the relationship between parent and child jobs. If a parent's job is canceled, all the children's jobs are canceled as well:

```scala 3
val expectedQueue = structured {
  val queue = new ConcurrentLinkedQueue[String]()
  val job1 = fork {
    val innerJob = fork {
      fork {
        Thread.sleep(3000)
        println("inner-inner-Job")
        queue.add("inner-inner-Job")
      }
      Thread.sleep(2000)
      println("innerJob")
      queue.add("innerJob")
    }
    Thread.sleep(1000)
    queue.add("job1")
  }
  val job = fork {
    Thread.sleep(500)
    job1.cancel()
    queue.add("job2")
    43
  }
  queue
}
expectedQueue.toArray should contain theSameElementsInOrderAs List("job2")
```

Trying to get the value from a canceled job will throw an `InterruptedException`. However, joining a canceled job will not throw any exception.

**You won't pay any additional cost for canceling a job**. The cancellation mechanism is based on the interruption of the virtual thread. No new structured scope is created for the cancellation mechanism.

## Contributing

If you want to contribute to the project, please do it! Any help is welcome.

## Acknowledgments

This project is inspired by the [Ox](https://github.com/softwaremill/ox) and
the [Unwrapped](https://github.com/xebia-functional/Unwrapped) libraries.


