package com.oomatomo.finagle

import com.twitter.finagle._
import com.twitter.finagle.http.{ Method, Request, Response, Status }
import com.twitter.finagle.redis.util.StringToChannelBuffer
import com.twitter.util.{ Await, Closable }
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, FunSuite }

/**
 * バージョン系の確認テスト
 */
class ServerTest extends FunSuite with BeforeAndAfterEach with BeforeAndAfterAll with ServerComponent {
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
  override def beforeAll(): Unit = {
    Await.result(redisMasterClient.flushAll())
    Await.result(redisSlaveClient.flushAll())
  }
  override def afterAll(): Unit = {
    Await.result(redisMasterClient.flushAll())
    Await.result(redisSlaveClient.flushAll())
  }
  test("HealthCheck Ok") {
    val request = http.Request(Method.Get, "/health_check")
    request.host = "localhost"
    val responseFuture = client(request)
    val response = Await.result(responseFuture)
    assert(response.status === Status.Ok)
    assert(response.contentString === "ok")
  }
  test("job Ok") {
    val request = http.Request(Method.Get, "/job")
    request.host = "localhost"
    val responseFuture = client(request)
    val response = Await.result(responseFuture)
    assert(response.status === Status.Ok)
    assert(response.contentString === "ok")
    val size = Await.result(redisMasterClient.lLen(StringToChannelBuffer("test_queue")))
    assert(size == 1)
  }
}