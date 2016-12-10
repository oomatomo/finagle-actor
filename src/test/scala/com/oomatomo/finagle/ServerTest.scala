package com.oomatomo.finagle

import com.twitter.finagle._
import com.twitter.finagle.http.{ Method, Request, Response, Status }
import com.twitter.util.{ Await, Closable }
import org.scalatest.{ BeforeAndAfterEach, FunSuite }

/**
  * バージョン系の確認テスト
  */
class ServerVersionTest extends FunSuite with BeforeAndAfterEach with ServerComponent {
  var server: com.twitter.finagle.ListeningServer = _
  var client: Service[Request, Response] = _

  override def beforeEach(): Unit = {
    server = Http.serve(s":9000", router)
    client = Http.newService(s"localhost:9000")
  }
  override def afterEach(): Unit = {
    Closable.all(server, client).close
    ()
  }

  test("HealthCheck Ok") {
    val request = http.Request(Method.Get, "/health_check")
    request.host = "localhost"
    val responseFuture = client(request)
    val response = Await.result(responseFuture)
    assert(response.status === Status.Ok)
    assert(response.contentString === "ok")
  }
}