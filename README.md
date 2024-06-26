<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://github.com/rcardin/sus4s/assets/16861531/ff6ff1fb-fb9f-45fc-a933-59d0036d4a60">
    <source media="(prefers-color-scheme: light)" srcset="https://github.com/rcardin/sus4s/assets/16861531/be98576b-e5c3-443b-9b67-cf291ecd7b79">
    <img alt="sus4s: A Direct-Style Scala Wrapper around the Structured Concurrency of Project Loom"
         src="https://github.com/rcardin/sus4s/assets/16861531/ff6ff1fb-fb9f-45fc-a933-59d0036d4a60"
         width="20%">
  </picture>
  <br/><br/>
  <div>
    <img src="https://img.shields.io/github/actions/workflow/status/rcardin/sus4s/scala.yml?branch=main" alt="GitHub Workflow Status (with branch)">
    <img src="https://img.shields.io/maven-central/v/in.rcard.sus4s/core_3" alt="Maven Central">
    <img src="https://img.shields.io/github/v/release/rcardin/sus4s" alt="GitHub release (latest by date)">
    <a href="https://javadoc.io/doc/in.rcard.sus4s/core_3">
      <img src="https://javadoc.io/badge2/in.rcard.sus4s/core_3/javadoc.svg" alt="javadoc">
    </a>
  </div>
</div>  

# sus4s

A Direct-Style Scala Wrapper Around the Structured Concurrency of Project Loom

## Dependency

The library is available on Maven Central. To use it, add the following dependency to your `build.sbt` files:

```sbt
libraryDependencies += "in.rcard.sus4s" %% "core" % "0.0.3"
```

The library is only available for Scala 3.

## Usage

The library provides a direct-style API for Project Loom's structured concurrency. It requires JDK 21 and Scala 3. Moreover, Java preview features must be enabled in the Scala compiler.

The main entry point is the `sus4s` package object. The following code snippet shows how to use the library:

```scala 3
import in.rcard.sus4s.sus4s.*
import scala.concurrent.duration.*

val result: Int = structured {
  val job1: Job[Int] = fork {
    delay(1.second)
    42
  }
  val job2: Job[Int] = fork {
    delay(500.millis)
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
  delay(1.second)
  42
}
val meaningOfLife: Int = job1.value
```

If you're not interested in the result of the function, you can use the `join` method:

```scala 3
val job1: Job[Int] = fork {
  delay(1.second)
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
        delay(2.seconds)
        queue.add("cancellable")
      }
    }
    delay(1.second)
    innerCancellableJob.cancel()
    queue.add("job1")
  }
  val job = fork {
    delay(500.millis)
    queue.add("job2")
    43
  }
  job.value
}
queue.toArray should contain theSameElementsInOrderAs List("job2", "job1")
result shouldBe 43
```

Cancellation is collaborative. In the above example, the job `innerCancellableJob` is marked for cancellation by the call `innerCancellableJob.cancel()`. However, the job is not immediately canceled. The job is canceled when it reaches the first point operation that can be _interrupted_ by the JVM. Hence, cancellation is based on the concept of interruption. In the above example, the `innerCancellableJob` is canceled when it reaches the `delay(2.seconds)` operation. The job will never be canceled if we remove the `delay` operation. A similar behavior is implemented by Kotlin coroutines (see [Kotlin Coroutines - A Comprehensive Introduction / Cancellation](https://blog.rockthejvm.com/kotlin-coroutines-101/#7-cancellation) for further details).

Cancelling a job follows the relationship between parent and child jobs. If a parent's job is canceled, all the children's jobs are canceled as well:

```scala 3
val expectedQueue = structured {
  val queue = new ConcurrentLinkedQueue[String]()
  val job1 = fork {
    val innerJob = fork {
      fork {
        delay(3.seconds)
        println("inner-inner-Job")
        queue.add("inner-inner-Job")
      }
      delay(2.seconds)
      println("innerJob")
      queue.add("innerJob")
    }
    delay(1.second)
    queue.add("job1")
  }
  val job = fork {
    delay(500.millis)
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

## Racing Jobs

The library provides the `race` method to race two jobs. The `race` function returns the result of the first job that completes. The other job is canceled. The following code snippet shows how to use the `race` method:

```scala 3
val results = new ConcurrentLinkedQueue[String]()
val actual: Int | String = 
  race[Int, String](
    {
      delay(1.second)
      results.add("job1")
      throw new RuntimeException("Error")
    }, {
      delay(500.millis)
      results.add("job2")
      "42"
    }
  )
actual should be("42")
results.toArray should contain theSameElementsInOrderAs List("job2")
```

If the first job completes with an exception, the `race` method waits for the second job to complete. and returns the result of the second job. If the second job completes with an exception, the `race` method throws the first exception it encountered.

Each job adhere to the rules of structured concurrency. The `race` function is optimized. Every raced block creates more than one virtual thread under the hood, which should not be a problem for the Loom runtime.

## Contributing

If you want to contribute to the project, please do it! Any help is welcome.

## Acknowledgments

This project is inspired by the [Ox](https://github.com/softwaremill/ox) and
the [Unwrapped](https://github.com/xebia-functional/Unwrapped) libraries.


