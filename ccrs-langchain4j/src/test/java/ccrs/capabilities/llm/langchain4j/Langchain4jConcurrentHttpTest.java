package ccrs.capabilities.llm.langchain4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Langchain4jConcurrentHttpTest {

    @Test
    void defaultOpenAiModelSendsTwoRequestsBeforeEitherResponseIsReleased() throws Exception {
        CountDownLatch arrivals = new CountDownLatch(2);
        CountDownLatch releaseResponses = new CountDownLatch(1);
        ExecutorService serverExecutor = Executors.newFixedThreadPool(2);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverExecutor);
        server.createContext("/", exchange -> respondAfterBarrier(exchange, arrivals, releaseResponses));
        server.start();

        Langchain4jLlmClient client = Langchain4jLlmClient.builder()
            .config(Langchain4jConfig.builder()
                .apiKey("local-test-key")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                .modelName("local-test-model")
                .timeout(Duration.ofSeconds(10))
                .maxRetries(0)
                .build())
            .build();

        try {
            Future<String> first = callers.submit(() -> client.complete("first"));
            Future<String> second = callers.submit(() -> client.complete("second"));

            assertTrue(arrivals.await(5, TimeUnit.SECONDS),
                "both HTTP requests must arrive before either response is released");
            releaseResponses.countDown();

            assertEquals("ok", first.get(5, TimeUnit.SECONDS));
            assertEquals("ok", second.get(5, TimeUnit.SECONDS));
        } finally {
            releaseResponses.countDown();
            server.stop(0);
            callers.shutdownNow();
            serverExecutor.shutdownNow();
        }
    }

    private static void respondAfterBarrier(
            HttpExchange exchange,
            CountDownLatch arrivals,
            CountDownLatch releaseResponses) throws IOException {
        try (exchange) {
            exchange.getRequestBody().readAllBytes();
            arrivals.countDown();
            if (!arrivals.await(5, TimeUnit.SECONDS)
                    || !releaseResponses.await(5, TimeUnit.SECONDS)) {
                exchange.sendResponseHeaders(504, -1);
                return;
            }

            byte[] body = ("{\"id\":\"chatcmpl-local\",\"object\":\"chat.completion\","
                + "\"created\":1,\"model\":\"local-test-model\",\"choices\":[{"
                + "\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},"
                + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,"
                + "\"completion_tokens\":1,\"total_tokens\":2}}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("test server interrupted", e);
        }
    }
}
