package br.com.ia.services.client.assitants;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.ia.model.ChatCompletionRequest;
import br.com.ia.services.client.IAIClient;
import br.com.ia.services.client.IaCallResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OpenAiAssistantsClient implements IAIClient {

	private static final String JSON = "application/json";
	private static final String BEARER = "Bearer ";
	private static final String BASE_URL = "https://api.openai.com/v1";

	private static final WebClient webClient = WebClient.create(BASE_URL);

	@Override
	@Retry(name = "assistantsClientRetry")
	@CircuitBreaker(name = "assistantsClient", fallbackMethod = "fallbackAssistants")
	public IaCallResult call(ChatCompletionRequest req) {
		String apiKey = req.getApiKey();
		String assistantId = req.getAssistantId();
		String prompt = req.getMessages().get(req.getMessages().size() - 1).getContent();

		// Cria thread e run
		String threadId = createThread(apiKey);
		String runId = createRun(threadId, assistantId, prompt, apiKey);

		// Polling até conclusão
		HttpHeaders headers = new HttpHeaders();
		for (int i = 0; i < 20; i++) {
			RunStatusResult result = getRunStatusWithHeaders(threadId, runId, apiKey);
			headers = result.headers();
			if ("completed".equals(result.status())) break;
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		// Recupera resposta final
		String resposta = getLastResponse(threadId, apiKey);

		return IaCallResult.builder()
				.resposta(resposta)
				.headers(headers)
				.modelo(headers.getFirst("openai-model"))
				.runId(runId)
				.build();
	}

	private String createThread(String apiKey) {
		JsonNode response = webClient.post()
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

	private String createRun(String threadId, String assistantId, String prompt, String apiKey) {
		var body = Map.of(
				"assistant_id", assistantId,
				"messages", List.of(Map.of("role", "user", "content", prompt))
		);

		JsonNode response = webClient.post()
				.uri("/threads/{threadId}/runs", threadId)
				.header(HttpHeaders.AUTHORIZATION, BEARER + apiKey)
				.header(HttpHeaders.CONTENT_TYPE, JSON)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(JsonNode.class)
				.block();

		if (response == null || response.get("id") == null) {
			throw new IllegalStateException("Erro ao criar run: resposta inválida.");
		}

		return response.get("id").asText();
	}

	public record RunStatusResult(String status, HttpHeaders headers) {}

	private RunStatusResult getRunStatusWithHeaders(String threadId, String runId, String apiKey) {
		var responseEntity = webClient.get()
				.uri("/threads/{threadId}/runs/{runId}", threadId, runId)
				.header(HttpHeaders.AUTHORIZATION, BEARER + apiKey)
				.header(HttpHeaders.CONTENT_TYPE, JSON)
				.retrieve()
				.toEntity(JsonNode.class)
				.block();

		if (responseEntity == null) {
			throw new IllegalStateException("Erro ao obter status do run: resposta nula.");
		}

		JsonNode body = responseEntity.getBody();
		if (body == null || !body.has("status")) {
			throw new IllegalStateException("Campo 'status' ausente na resposta.");
		}

		return new RunStatusResult(
				body.get("status").asText(),
				responseEntity.getHeaders() 
			);
	}

	private String getLastResponse(String threadId, String apiKey) {
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

	public IaCallResult fallbackAssistants(ChatCompletionRequest req, Throwable ex) {
		log.error("Assistants API falhou, req={}:", req, ex);
		return IaCallResult.builder()
				.resposta("Desculpe, estamos com instabilidade no Assistants. Tente novamente mais tarde.")
				.headers(new HttpHeaders())
				.modelo("fallback")
				.runId(null)
				.build();
	}
}
