import in.rcard.sus4s.sus4s
import in.rcard.sus4s.sus4s.{fork, forkCancellable, structured}
import org.scalatest.TryValues.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.ConcurrentLinkedQueue
import scala.util.Try

class StructuredSpec extends AnyFlatSpec with Matchers {

  "A structured program" should "wait the completion of all the forked jobs" in {
    val results = structured {
      val queue = new ConcurrentLinkedQueue[String]()
      val job1 = fork {
        Thread.sleep(1000)
        queue.add("job1")
      }
      val job2 = fork {
        Thread.sleep(500)
        queue.add("job2")
      }
      val job3 = fork {
        Thread.sleep(100)
        queue.add("job3")
      }
      queue
    }

    results.toArray should contain theSameElementsInOrderAs List("job3", "job2", "job1")
  }

  it should "stop the execution if one the job throws an exception" in {
    val results = new ConcurrentLinkedQueue[String]()
    val tryResult = Try {
      structured {
        val job1 = fork {
          Thread.sleep(1000)
          results.add("job1")
        }
        val job2 = fork {
          Thread.sleep(500)
          results.add("job2")
          throw new RuntimeException("Error")
        }
        val job3 = fork {
          Thread.sleep(100)
          results.add("job3")
        }
      }
    }

    tryResult.failure.exception shouldBe a[RuntimeException]
    tryResult.failure.exception.getMessage shouldBe "Error"
    results.toArray should contain theSameElementsInOrderAs List("job3", "job2")
  }

  it should "stop the execution if the block throws an exception" in {
    val results = new ConcurrentLinkedQueue[String]()
    val tryResult = Try {
      structured {
        val job1 = fork {
          Thread.sleep(1000)
          results.add("job1")
        }
        val job2 = fork {
          Thread.sleep(500)
          results.add("job2")
        }
        val job3 = fork {
          Thread.sleep(100)
          results.add("job3")
        }
        throw new RuntimeException("Error")
      }
    }

    tryResult.failure.exception shouldBe a[RuntimeException]
    tryResult.failure.exception.getMessage shouldBe "Error"
    results.toArray shouldBe empty
  }

  it should "join the values of different jobs" in {
    val queue = new ConcurrentLinkedQueue[String]()
    val result = structured {
      val job1 = fork {
        Thread.sleep(1000)
        queue.add("job1")
        42
      }
      val job2 = fork {
        Thread.sleep(500)
        queue.add("job2")
        43
      }
      job1.value + job2.value
    }

    queue.toArray should contain theSameElementsInOrderAs List("job2", "job1")
    result shouldBe 85
  }

  it should "cancel at the first suspending point" in {
    val queue = new ConcurrentLinkedQueue[String]()
    val result = structured {
      val cancellable = forkCancellable {
        while (true) {
          Thread.sleep(2000)
          println("cancellable job")
          queue.add("cancellable")
        }
      }
      val job = fork {
        cancellable.cancel()
        queue.add("job2")
        43
      }
      job.value
    }

    queue.toArray should contain theSameElementsInOrderAs List("job2")
    result shouldBe 43
  }
}
