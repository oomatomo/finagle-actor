package com.oomatomo.finagle

import akka.actor._
import akka.util.Timeout
import com.redis.RedisClientPool
import com.twitter.finagle._
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.{ Request, Response }
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.http.path._
import com.twitter.finagle.redis.util.StringToChannelBuffer
import com.twitter.finagle.redis.{ Client, Redis }
import com.twitter.util.{ Await, Future }
import org.joda.time.DateTime
import akka.pattern.ask
import akka.pattern.gracefulStop
import scala.concurrent.duration._

class HealthCheckService(redisMasterClient: redis.Client, redisSlaveClient: redis.Client)
    extends Service[http.Request, http.Response] {

  def apply(request: http.Request): Future[http.Response] = {

    val blankResponse = http.Response(request.version, http.Status.Ok)

    redisMasterClient.ping().transform { tryMasterPing =>
      if (tryMasterPing.isReturn) {
        redisSlaveClient.ping().transform { trySlavePing =>
          if (trySlavePing.isReturn) {
            blankResponse.setContentString("ok")
            Future.value(blankResponse)
          } else {
            blankResponse.setContentString("ng")
            Future.value(blankResponse)
          }
        }
      } else {
        blankResponse.setContentString("ng")
        Future.value(blankResponse)
      }
    }
  }
}

class JobService(redisMasterClient: redis.Client)
    extends Service[http.Request, http.Response] {

  def apply(request: http.Request): Future[http.Response] = {

    val blankResponse = http.Response(request.version, http.Status.Ok)
    blankResponse.setContentString("ok")

    val key = StringToChannelBuffer("test_queue")
    val value = List(StringToChannelBuffer(s"test_value_${new DateTime().getMillis / 1000}"))
    redisMasterClient.rPush(key, value).transform { tryRedis =>
      if (tryRedis.isThrow) {
        println(s"error ${tryRedis.throwable.toString}")
      }
      Future.value(blankResponse)
    }

  }
}

object Main extends App with ServerComponent with ActorComponent {
  import system.dispatcher
  val mainThread = Thread.currentThread()

  system.scheduler.schedule(Duration.Zero, 2 seconds, managerActor, Manager.Job1)
  system.scheduler.schedule(Duration.Zero, 2 seconds, managerActor, Manager.Job2)

  val server = Http.serve(s":9000", router)
  Runtime.getRuntime().addShutdownHook(new Thread(
    new Runnable {
      def run() {
        managerActor ! Manager.ShutdownChildActor
        implicit val timeout = Timeout(5 seconds)
        // managerActorのステータスの値の値の確認を行う
        var keepRunning = true
        while (keepRunning) {
          val r = scala.concurrent.Await.result(managerActor ? Manager.Status, timeout.duration).asInstanceOf[String]
          if (r == "false") {
            keepRunning = false
          }
          Thread.sleep(1000)
        }

        // 親アクターを停止
        try {
          val stopped: scala.concurrent.Future[Boolean] = gracefulStop(managerActor, 5 seconds)
          scala.concurrent.Await.result(stopped, 5 seconds)
          println("stop managerActor success")
        } catch {
          // actorが５秒以内に停止しなかった場合
          case e: akka.pattern.AskTimeoutException =>
            println("error stop managerActor")
        }
        scala.concurrent.Await.result(system.terminate(), 5 seconds)
        //Await.ready(server.close(15 seconds))
        mainThread.join()
      }
    }))
}

trait ServerComponent {

  val redisMasterClient = Client(
    ClientBuilder()
      .hosts("127.0.0.1:6379")
      .hostConnectionLimit(10000)
      .codec(Redis())
      .daemon(true)
      .buildFactory())

  val redisSlaveClient = Client(
    ClientBuilder()
      .hosts("127.0.0.1:6380")
      .hostConnectionLimit(10000)
      .codec(Redis())
      .daemon(true)
      .buildFactory())

  // Not Foundを返す
  val notFoundService = new Service[Request, Response] {
    def apply(request: http.Request): Future[http.Response] = {
      val rep = http.Response(request.version, http.Status.NotFound)
      rep.setContentString("")
      Future.value(rep)
    }
  }

  val healthCheckService = new HealthCheckService(redisMasterClient, redisSlaveClient)
  val JobService = new JobService(redisMasterClient)

  val router = RoutingService.byPathObject[Request] {
    case Root / "health_check" => healthCheckService
    case Root / "job"          => JobService
    case _                     => notFoundService
  }
}

object Manager {
  case object Start
  case object Status
  case object ShutdownChildActor
  case object Job1
  case object Job2
}

class ManagerActor extends Actor with ActorLogging {

  val job1Actor: ActorRef = context.watch(context.actorOf(Props[Job1Actor], "Job1Actor"))
  val job2Actor: ActorRef = context.watch(context.actorOf(Props[Job2Actor], "Job2Actor"))

  def receive: Actor.Receive = {
    case Manager.Job1 =>
      job1Actor ! "done"
    case Manager.Job2 =>
      job2Actor ! "done"
    case Manager.ShutdownChildActor =>
      context.stop(job1Actor)
      context.stop(job2Actor)
      context.become(shuttingDown)
  }
  var isAliveJob1Actor = true
  var isAliveJob2Actor = true

  def shuttingDown: Receive = {
    case Manager.Status =>
      log.info("stopping  ChildActor")
      sender ! (isAliveJob1Actor && isAliveJob2Actor).toString
    case Terminated(`job1Actor`) =>
      isAliveJob1Actor = false
      log.info("catch stopped on job1")
    case Terminated(`job2Actor`) =>
      isAliveJob2Actor = false
      log.info("catch stopped on job2")
  }

  override def postStop(): Unit = {
    log.info("stopped ManagerActor")
  }
}

class Job1Actor extends Actor with ActorLogging {

  def receive: Actor.Receive = {
    case "done" =>
      log.info("start job 1")
      val redisMasterClientPool = new RedisClientPool("127.0.0.1", 6379)
      val redisSlaveClientPool = new RedisClientPool("127.0.0.1", 6380)

      var keep = true
      while (keep) {
        redisMasterClientPool.withClient { client => client.lindex("test_queue", 0) } match {
          case Some(value) =>
            redisSlaveClientPool.withClient { client => client.rpush("test_queue_1_", value) }
            redisMasterClientPool.withClient { client => client.lpop("test_queue") }
          case None =>
            keep = false
        }

      }

      log.info("end job 1")
    case "stop" =>
      context stop self
  }

  override def postStop() = {
    log.info("stopped job 1")
  }
}

class Job2Actor extends Actor with ActorLogging {

  val redisClientPool = new RedisClientPool("127.0.0.1", 6780)

  def receive: Actor.Receive = {
    case "done" =>
      log.info("start job 2")
      Thread.sleep(10000)
      log.info("end job 2")
    case "stop" =>
      context stop self
  }

  override def postStop() = {
    log.info("stopped job 2")
  }
}

trait ActorComponent {
  val system = ActorSystem("DaemonSystem")
  val managerActor: ActorRef = system.actorOf(Props(classOf[ManagerActor]))
}