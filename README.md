![GitHub Workflow Status (with branch)](https://img.shields.io/github/actions/workflow/status/rcardin/sus4s/scala.yml?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/in.rcard.sus4s/core_3)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/rcardin/sus4s)
[![javadoc](https://javadoc.io/badge2/in.rcard.sus4s/core_3/javadoc.svg)](https://javadoc.io/doc/in.rcard.sus4s/core_3)

# sus4s 🎸🎶

A Direct-Style Scala Wrapper Around the Structural Concurrency of Project Loom

## Dependency

The library is available on Maven Central. To use it, add the following dependency to your `build.sbt` files:

```sbt
libraryDependencies += "in.rcard.sus4s" %% "core" % "0.0.1"
```

The library is only available for Scala 3.

## Usage

The library provides a direct-style API for the structured concurrency of Project Loom. The library requires JDK 21 and
Scala 3. Moreover, Java preview features must be enabled in the Scala compiler.

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

The `structured` method creates a new structured concurrency scope represented by the `Suspend` trait. It's built on top
of the `java.util.concurrent.StructuredTaskScope` class. Hence, the threads forked inside the `structured` block are
Java Virtual Threads.

The `fork` method creates a new Java Virtual Thread that executes the given block of code. The `fork` method executes
functions declared with the capability of suspend:

```scala 3
def findUserById(id: UserId): Suspend ?=> User
```

Coloring a function with the `Suspend` capability tells the caller that the function performs a suspendable operation,
aka some effect. Suspension is managed by the Loom runtime, which is responsible for scheduling the virtual threads.

A type alias is available for the `Suspend` capability:

```scala 3
type Suspended[A] = Suspend ?=> A
```

So, the above function can be rewritten as:

```scala 3
def findUserById(id: UserId): Suspended[User]
```

Forking a suspendable function means creating a new virtual thread that executes the function. The thread is represented
by the `Job` class. The `Job` class provides the `value` method that waits for the completion of the virtual thread and
returns the result of the function.

The `structured` function uses structured concurrency to run the suspendable tasks. In detail, it ensures that the
thread executing the block waits for the completion of all the forked tasks. The structured blocks terminates when:

- all the forked tasks complete successfully
- one of the forked tasks throws an exception
- the block throws an exception

The `structured` function is completely transparent to any exception thrown by the block or by any of the forked tasks.

## Contributing

If you want to contribute to the project, please do it! Any help is welcome.

## Acknowledgments

This project is inspired by the [Ox](https://github.com/softwaremill/ox) and
the [Unwrapped](https://github.com/xebia-functional/Unwrapped) libraries.


