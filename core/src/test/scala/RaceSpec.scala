import in.rcard.sus4s.sus4s.{delay, race, structured}
import org.scalatest.TryValues.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.*
import scala.util.Try

class RaceSpec extends AnyFlatSpec with Matchers {
  "Racing two functions" should "return the result of the first one that completes and cancel the execution of the other" in {
    val results = new ConcurrentLinkedQueue[String]()
    val actual: Int | String = structured {
      race(
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
    }

    actual should be("42")
    results.toArray should contain theSameElementsInOrderAs List("job2")
  }

  it should "return the result of the second one if the first one throws an exception" in {
    val actual: Int | String = structured {
      race(
        {
          delay(1.second)
          42
        }, {
          delay(500.millis)
          throw new RuntimeException("Error")
        }
      )
    }

    actual should be(42)
  }

  it should "throw the exception thrown by the first function both throw an exception" in {
    val expectedResult = Try {
      structured {
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
    }

    expectedResult.failure.exception shouldBe a[RuntimeException]
    expectedResult.failure.exception.getMessage shouldBe "Error in job2"
  }
}
