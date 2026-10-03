package onion.llm

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.sun.net.httpserver.{HttpExchange, HttpServer}

import java.net.{InetSocketAddress, ServerSocket}
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/**
 * A local stand-in for the Messages API: a `com.sun.net.httpserver.HttpServer` that
 * records every request and answers with canned JSON in the documented response shapes.
 * No test ever reaches api.anthropic.com.
 */
final class FakeAnthropic(respond: FakeAnthropic.Request => FakeAnthropic.Reply) extends AutoCloseable {
  import FakeAnthropic.*

  private val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
  private val captured = new ConcurrentLinkedQueue[Request]()

  server.createContext("/", (exchange: HttpExchange) => {
    val body = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
    val headers = exchange.getRequestHeaders.asScala.map { case (k, v) => k.toLowerCase -> v.asScala.mkString(",") }.toMap
    val request = Request(exchange.getRequestMethod, exchange.getRequestURI.toString, headers, body)
    captured.add(request)
    val reply = respond(request)
    val bytes = reply.body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.add("content-type", "application/json")
    exchange.getResponseHeaders.add("request-id", "req_fake")
    reply.headers.foreach { case (k, v) => exchange.getResponseHeaders.add(k, v) }
    exchange.sendResponseHeaders(reply.status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
    exchange.close()
  })
  server.start()

  def url: String = s"http://127.0.0.1:${server.getAddress.getPort}"
  def requests: List[Request] = captured.asScala.toList
  def lastJson: JsonNode = Json.readTree(requests.last.body)

  override def close(): Unit = server.stop(0)
}

object FakeAnthropic {
  val Json = new ObjectMapper()

  final case class Request(method: String, uri: String, headers: Map[String, String], body: String) {
    def json: JsonNode = Json.readTree(body)
    /** The user prompt of the request's first message, whichever form the SDK sent it in. */
    def prompt: String = {
      val content = json.path("messages").path(0).path("content")
      if (content.isTextual) content.asText() else content.path(0).path("text").asText()
    }
  }

  final case class Reply(status: Int, body: String, headers: Map[String, String] = Map.empty)

  private def str(s: String): String = Json.writeValueAsString(s)

  /** A Messages API response: an (omitted-display) thinking block, then the answer's text. */
  def message(text: String, stopReason: String = "end_turn"): Reply = Reply(200,
    s"""{"id":"msg_fake","type":"message","role":"assistant","model":"claude-opus-5-5",
       |"content":[{"type":"thinking","thinking":"","signature":"sig_fake"},{"type":"text","text":${str(text)}}],
       |"stop_reason":"$stopReason","stop_sequence":null,
       |"usage":{"input_tokens":12,"output_tokens":34}}""".stripMargin)

  /** A refusal: HTTP 200, empty content, `stop_reason: "refusal"` with `stop_details`. */
  def refusal(category: String, explanation: String): Reply = Reply(200,
    s"""{"id":"msg_fake","type":"message","role":"assistant","model":"claude-opus-5-5",
       |"content":[],"stop_reason":"refusal","stop_sequence":null,
       |"stop_details":{"type":"refusal","category":${str(category)},"explanation":${str(explanation)}},
       |"usage":{"input_tokens":12,"output_tokens":0}}""".stripMargin)

  /** An API error body. `x-should-retry: false` keeps a retryable status from being retried. */
  def error(status: Int, tpe: String, message: String, retry: Boolean = false): Reply = Reply(status,
    s"""{"type":"error","error":{"type":"$tpe","message":${str(message)}},"request_id":"req_fake"}""",
    if (retry) Map("retry-after-ms" -> "1") else Map("x-should-retry" -> "false"))

  /** A local port nothing listens on, for a refused connection. */
  def deadUrl(): String = {
    val socket = new ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    s"http://127.0.0.1:$port"
  }
}
