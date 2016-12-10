package com.oomatomo.finagle

import com.twitter.finagle._
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.http.path._
import com.twitter.finagle.redis.{Client, Redis}
import com.twitter.util.{Await, Future}

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

object Main extends App with ServerComponent {
  val server = Http.serve(s":9000", router)
  Await.ready(server)
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

  val router = RoutingService.byPathObject[Request] {
    case Root / "health_check" => healthCheckService
    case _ => notFoundService
  }
}
