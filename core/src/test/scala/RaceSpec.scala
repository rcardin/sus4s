import in.rcard.sus4s.sus4s.{delay, fork, race, structured}
import org.scalatest.TryValues.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.*
import scala.util.Try

class RaceSpec extends AnyFlatSpec with Matchers {
  "Racing two functions" should "return the result of the first one that completes and cancel the execution of the other" in {
    val results = new ConcurrentLinkedQueue[String]()
    val actual: Int | String = race[Int, String](
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
  }

  it should "return the result of the second one if the first one throws an exception" in {
    val results = new ConcurrentLinkedQueue[String]()
    val actual: Int | String = race(
      {
        delay(1.second)
        results.add("job1")
        42
      }, {
        delay(500.millis)
        results.add("job2")
        throw new RuntimeException("Error")
      }
    )

    actual should be(42)
    results.toArray should contain theSameElementsInOrderAs List("job2", "job1")
  }

  it should "honor the structural concurrency and wait for all the jobs to complete" in {
    val results = new ConcurrentLinkedQueue[String]()
    val actual: Int | String = race(
      {
        val job1 = fork {
          fork {
            delay(2.second)
            results.add("job3")
          }
          delay(1.second)
          results.add("job1")
        }
        42
      }, {
        delay(500.millis)
        throw new RuntimeException("Error")
      }
    )

    actual should be(42)
    results.toArray should contain theSameElementsInOrderAs List("job1", "job3")
  }

  it should "throw the exception thrown by the first function both throw an exception" in {
    val expectedResult = Try {
      race(
        {
          delay(1.second)
          throw new RuntimeException("Error in job1")
        }, {
          delay(500.millis)
          throw new RuntimeException("Error in job2")
        }
      )
    }

    expectedResult.failure.exception shouldBe a[RuntimeException]
    expectedResult.failure.exception.getMessage shouldBe "Error in job2"
  }

  it should "honor the structural concurrency and return the value of the second function if the first threw an exception" in {
    val actual: Int | String = race(
      {
        val job1 = fork {
          delay(500.millis)
          println("job1")
          throw new RuntimeException("Error in job1")
        }
        42
      }, {
        delay(1.second)
        println("job2")
        "42"
      }
    )

    actual should be("42")
  }

  it should "honor the structured concurrency and cancel all the children jobs" in {
    val results = new ConcurrentLinkedQueue[String]()
    val actual: Int | String = race(
      {
        val job1 = fork {
          delay(1.seconds)
          results.add("job1")
        }
        42
      }, {
        delay(500.millis)
        results.add("job2")
        "42"
      }
    )

    Thread.sleep(2000)

    actual should be("42")
    results.toArray should contain theSameElementsInOrderAs List("job2")
  }
}
