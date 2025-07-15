package br.com.ia.services.client.assitants;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.ia.model.ChatCompletionRequest;
import br.com.ia.services.client.IAIClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OpenAiAssistantsClient  implements IAIClient {

	private static final String JSON = "application/json";
	private static final String BEARER = "Bearer ";
	private static final String BASE_URL = "https://api.openai.com/v1";
	
	static WebClient webClient = WebClient.create(BASE_URL);
	
    @Override
    @Retry(name = "assistantsClientRetry")
    @CircuitBreaker(name = "assistantsClient", fallbackMethod = "fallbackAssistants")
	public String call(ChatCompletionRequest req) {
		String apiKey = req.getApiKey();
		String assistantId = req.getAssistantId(); // precisa vir no request
		String prompt = req.getMessages().get(req.getMessages().size() - 1).getContent();

		// Cria thread (ou futura lógica para reutilizar via idChat)
		String threadId = createThread(apiKey);

		// Cria run
		String runId = createRun(threadId, assistantId, prompt, apiKey);

		// Poll até a conclusão
		for (int i = 0; i < 20; i++) {
			String status = getRunStatus(threadId, runId, apiKey);
			if ("completed".equals(status)) break;
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		// Retorna a última resposta
		return getLastResponse(threadId, apiKey);
	}

	public String createThread(String apiKey) {
		var response = webClient.post()
			.uri("/threads")
			.header(HttpHeaders.AUTHORIZATION, BEARER + apiKey)
			.header(HttpHeaders.CONTENT_TYPE, JSON)
			.retrieve()
			.bodyToMono(JsonNode.class)
			.block();

		if (response == null || response.get("id") == null) {
			throw new IllegalStateException("Resposta inválida da API ao criar thread.");
		}

		return response.get("id").asText();
	}

	public String createRun(String threadId, String assistantId, String prompt, String apiKey) {
		var body = Map.of(
			"assistant_id", assistantId,
			"messages", List.of(Map.of("role", "user", "content", prompt))
		);

		var response = webClient.post()
			.uri("/threads/{threadId}/runs", threadId)
			.header(HttpHeaders.AUTHORIZATION, BEARER + apiKey)
			.header(HttpHeaders.CONTENT_TYPE, JSON)
			.bodyValue(body)
			.retrieve()
			.bodyToMono(JsonNode.class)
			.block();

		if (response == null || response.get("id") == null) {
			throw new IllegalStateException("Erro ao criar run para a thread: resposta inválida.");
		}

		return response.get("id").asText();
	}

	public String getRunStatus(String threadId, String runId, String apiKey) {
		var response = webClient.get()
			.uri("/threads/{threadId}/runs/{runId}", threadId, runId)
			.header(HttpHeaders.AUTHORIZATION, BEARER + apiKey)
			.header(HttpHeaders.CONTENT_TYPE, JSON)
			.retrieve()
			.bodyToMono(JsonNode.class)
			.block();

		if (response == null || response.get("status") == null) {
			throw new IllegalStateException("Erro ao obter status do run: resposta inválida.");
		}

		return response.get("status").asText(); 
	}

	public String getLastResponse(String threadId, String apiKey) {
		JsonNode response = webClient.get()
				.uri("/threads/{threadId}/messages", threadId)
				.header(HttpHeaders.AUTHORIZATION, BEARER + apiKey)
				.header(HttpHeaders.CONTENT_TYPE, JSON)
				.retrieve()
				.bodyToMono(JsonNode.class)
				.block();

			if (response == null || !response.has("data") || !response.get("data").isArray()) {
				throw new IllegalStateException("Resposta inválida ao consultar mensagens da thread.");
			}

			var messages = response.get("data");
			if (messages.isEmpty()) {
				throw new IllegalStateException("Nenhuma mensagem encontrada para essa thread.");
			}

			var contentNode = messages.get(0).get("content");
			if (contentNode == null || !contentNode.isArray() || contentNode.isEmpty()) {
				throw new IllegalStateException("Conteúdo da mensagem está ausente ou malformado.");
			}

			var textNode = contentNode.get(0).get("text");
			if (textNode == null || textNode.get("value") == null) {
				throw new IllegalStateException("Texto da mensagem não encontrado.");
			}

			return textNode.get("value").asText();
	}
	
	public String fallbackAssistants(ChatCompletionRequest req, Throwable ex) {
		log.error("Assistants API falhou, req={}:", req, ex);
		return "Desculpe, estamos com instabilidade no Assistants. Tente novamente mais tarde.";
	}
}

