package ccrs.capabilities.llm.langchain4j;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Langchain4jAstraRequestTest {

    @Test
    void defaultRequestUsesMediumReasoningWithoutUnsupportedSamplingParameters() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = ("{\"id\":\"test\",\"object\":\"chat.completion\",\"created\":1,"
                    + "\"model\":\"gpt-6-astra\",\"choices\":[{\"index\":0,"
                    + "\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},"
                    + "\"finish_reason\":\"stop\"}]}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });
        server.start();
        try {
            Langchain4jLlmClient client = Langchain4jLlmClient.builder()
                .config(Langchain4jConfig.builder()
                    .apiKey("local-test-key")
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .timeoutSeconds(5)
                    .maxRetries(0)
                    .build())
                .build();

            assertEquals("ok", client.complete("test"));
            String body = request.get();
            assertTrue(Pattern.compile("\"model\"\\s*:\\s*\"gpt-6-astra\"").matcher(body).find(), body);
            assertTrue(Pattern.compile("\"reasoning_effort\"\\s*:\\s*\"medium\"").matcher(body).find(), body);
            for (String parameter : new String[] {"temperature", "top_p", "logprobs"}) {
                assertFalse(body.contains("\"" + parameter + "\""), body);
            }
        } finally {
            server.stop(0);
        }
    }
}
