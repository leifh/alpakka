/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.sqs.scaladsl

import java.util.concurrent.{ExecutorService, Executors}

import akka.stream.alpakka.sqs.SqsSourceSettings
import akka.stream.scaladsl.Sink
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.services.sqs.model.QueueDoesNotExistException
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AsyncWordSpec, Matchers}

class SqsSourceSpec extends AsyncWordSpec with ScalaFutures with Matchers with DefaultTestContext {

  private val sqsSourceSettings = SqsSourceSettings.Defaults

  "SqsSource" should {

    "stream a single batch from the queue" taggedAs Integration in {

      val queue = randomQueueUrl()
      sqsClient.sendMessage(queue, "alpakka")

      SqsSource(queue, sqsSourceSettings).take(1).runWith(Sink.head).map(_.getBody shouldBe "alpakka")

    }

    "stream a single batch from the queue with custom client" taggedAs Integration in {

      //#init-custom-client
      val executor: ExecutorService = Executors.newFixedThreadPool(10)
      implicit val sqsClient: AmazonSQSAsyncClient =
        new AmazonSQSAsyncClient(credentials, executor).withEndpoint[AmazonSQSAsyncClient]("http://localhost:9324")
      //#init-custom-client

      val queue = randomQueueUrl()
      sqsClient.sendMessage(queue, "alpakka")

      SqsSource(queue, sqsSourceSettings).take(1).runWith(Sink.head).map(_.getBody shouldBe "alpakka")
    }

    "stream multiple batches from the queue" taggedAs Integration in {

      val queue = randomQueueUrl()

      val input = 1 to 100 map { i =>
        s"alpakka-$i"
      }

      input foreach { m =>
        sqsClient.sendMessage(queue, m)
      }

      //#run
      SqsSource(queue, sqsSourceSettings).take(100).runWith(Sink.seq).map(_ should have size 100)
      //#run

    }

    "continue streaming if receives an empty response" taggedAs Integration in {

      val queue = randomQueueUrl()

      val f = SqsSource(queue, SqsSourceSettings(0, 100, 10)).take(1).runWith(Sink.seq)

      sqsClient.sendMessage(queue, s"alpakka")

      f.map(_ should have size 1)
    }

    "should finish immediately if the queue does not exist" taggedAs Integration in {

      val queue = "http://localhost:9324/queue/not-existing"

      val f = SqsSource(queue, sqsSourceSettings).runWith(Sink.seq)

      f.failed.map(_ shouldBe a[QueueDoesNotExistException])
    }
  }
}
