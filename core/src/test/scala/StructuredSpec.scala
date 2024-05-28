import in.rcard.sus4s.sus4s
import in.rcard.sus4s.sus4s.{fork, structured}
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

  it should "wait for children jobs to finish" in {
    val results = structured {
      val queue = new ConcurrentLinkedQueue[String]()
      val job1 = fork {
        fork {
          Thread.sleep(1000)
          queue.add("1")
        }
        fork {
          Thread.sleep(500)
          queue.add("2")
        }
        queue.add("3")
      }
      queue
    }

    results.toArray should contain theSameElementsInOrderAs List("3", "2", "1")
  }

  "cancellation" should "cancel at the first suspending point" in {
    val expectedQueue = structured {
      val queue = new ConcurrentLinkedQueue[String]()
      val cancellable = fork {
        Thread.sleep(2000)
        queue.add("cancellable")
      }
      val job = fork {
        Thread.sleep(500)
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
        Thread.sleep(2000)
        queue.add("cancellable")
      }
      val job = fork {
        Thread.sleep(500)
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
          Thread.sleep(2000)
          queue.add("cancellable")
        }
        Thread.sleep(1000)
        innerCancellableJob.cancel()
        queue.add("job1")
      }
      val job = fork {
        Thread.sleep(500)
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
  }

  it should "not throw any exception when joining a cancelled job" in {
    val expected = structured {
      val cancellable = fork {
        Thread.sleep(2000)
      }
      Thread.sleep(500)
      cancellable.cancel()
      cancellable.join()
      42
    }

    expected shouldBe 42
  }

  it should "throw an exception when asking for the value of a cancelled job" in {
    assertThrows[InterruptedException] {
      structured {
        val cancellable = fork {
          Thread.sleep(2000)
        }
        Thread.sleep(500)
        cancellable.cancel()
        cancellable.value
      }
    }
  }
}
