package br.com.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Collections;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import br.com.ia.model.ChatCompletionRequest;
import br.com.ia.model.ChatMessage;
import br.com.ia.services.client.OpenAiClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * Teste de integração para o OpenAiWebClient. Usa MockWebServer para simular a
 * API da OpenAI.
 */
@SpringBootTest(classes = TestApplication.class, properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration" })
@TestInstance(Lifecycle.PER_CLASS)
class OpenAiWebClientIntegrationTest {

	private static MockWebServer mockWebServer;

	@Autowired
	private OpenAiClient openAiClient;

	@DynamicPropertySource
	static void overrideProperties(DynamicPropertyRegistry registry) throws IOException {
		mockWebServer = new MockWebServer();
		mockWebServer.start();
		String baseUrl = mockWebServer.url("/v1/chat/completions").toString();
		registry.add("ia.openai.url", () -> baseUrl);
		registry.add("ia.openai.key", () -> "test-key");
		registry.add("ia.openai.model", () -> "test-model");
	}

	@AfterAll
	void shutdown() throws IOException {
		mockWebServer.shutdown();
	}

	@Test
	void testCallReturnsExpectedContent() {
		// Mock de resposta da OpenAI
		String jsonResponse = "{\n" + "  \"choices\": [\n" + "    {\n"
				+ "      \"message\": { \"content\": \"Hello, Test!\" }\n" + "    }\n" + "  ]\n" + "}";
		mockWebServer.enqueue(new MockResponse().setBody(jsonResponse).addHeader("Content-Type", "application/json"));

		// Monta requisição
		ChatCompletionRequest req = ChatCompletionRequest.builder().provider("openai").model("GPT-4o")
				.apiKey("764736736473647364736")
				.messages(Collections.singletonList(new ChatMessage("user", "Test prompt"))).temperature(0.5).build();

		// Executa chamada
		String result = openAiClient.call(req);

		// Verifica
		assertEquals("Hello, Test!", result);
	}
}
