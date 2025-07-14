package br.com.ia.services;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

@Service
public class OpenAiAssistantsClientImpl implements OpenAiAssistantsClient {

	private static final String JSON = "application/json";
	private static final String BEARER = "Bearer ";
	private static final String BASE_URL = "https://api.openai.com/v1";

	private final WebClient webClient;

	public OpenAiAssistantsClientImpl(WebClient.Builder builder) {
		this.webClient = builder.baseUrl(BASE_URL).build();
	}

	@Override
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

	@Override
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

	@Override
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

	@Override
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
}

