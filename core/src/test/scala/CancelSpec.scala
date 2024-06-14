import in.rcard.sus4s.sus4s
import in.rcard.sus4s.sus4s.{delay, fork, structured}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.*

class CancelSpec extends AnyFlatSpec with Matchers {
  "cancellation" should "cancel at the first suspending point" in {
    val expectedQueue = structured {
      val queue = new ConcurrentLinkedQueue[String]()
      val cancellable = fork {
        delay(2.seconds)
        queue.add("cancellable")
      }
      val job = fork {
        delay(500.millis)
        cancellable.cancel()
        queue.add("job2")
      }
      queue
    }
    expectedQueue.toArray should contain theSameElementsInOrderAs List("job2")
  }

  it should "not throw an exception if joined" in {

    val expectedQueue = structured {
      val queue = new ConcurrentLinkedQueue[String]()
      val cancellable = fork {
        delay(2.seconds)
        queue.add("cancellable")
      }
      val job = fork {
        delay(500.millis)
        cancellable.cancel()
        queue.add("job2")
      }
      cancellable.join()
      queue
    }
    expectedQueue.toArray should contain theSameElementsInOrderAs List("job2")
  }

  it should "not cancel parent job" in {

    val expectedQueue = structured {
      val queue = new ConcurrentLinkedQueue[String]()
      val job1 = fork {
        val innerCancellableJob = fork {
          delay(2.seconds)
          queue.add("cancellable")
        }
        delay(1.second)
        innerCancellableJob.cancel()
        queue.add("job1")
      }
      val job = fork {
        delay(500.millis)
        queue.add("job2")
      }
      queue
    }
    expectedQueue.toArray should contain theSameElementsInOrderAs List("job2", "job1")
  }

  it should "cancel children jobs" in {
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
      }
      queue
    }
    expectedQueue.toArray should contain theSameElementsInOrderAs List("job2")
  }

  it should "not throw any exception when joining a cancelled job" in {
    val expected = structured {
      val cancellable = fork {
        delay(2.seconds)
      }
      delay(500.millis)
      cancellable.cancel()
      cancellable.join()
      42
    }

    expected shouldBe 42
  }

  it should "not throw any exception if a job is canceled twice" in {
    val expected = structured {
      val cancellable = fork {
        delay(2.seconds)
      }
      delay(500.millis)
      cancellable.cancel()
      cancellable.cancel()
      42
    }

    expected shouldBe 42
  }

  it should "throw an exception when asking for the value of a cancelled job" in {
    assertThrows[InterruptedException] {
      structured {
        val cancellable = fork {
          delay(2.seconds)
        }
        delay(500.millis)
        cancellable.cancel()
        cancellable.value
      }
    }
  }
}
