package br.com.ia.services.client.assitants;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import br.com.ia.model.ChatCompletionRequest;
import br.com.ia.model.ChatCompletionResponse;
import br.com.ia.services.client.IAIClient;
import br.com.ia.services.client.IaCallResult;
import br.com.shared.exception.IAException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OpenAiClient implements IAIClient {

	private static final String BASE_URL = "https://api.openai.com/v1/chat/completions";
	private static final WebClient webClient = WebClient.create();

	@Override
	@CircuitBreaker(name = "iaClient", fallbackMethod = "fallback")
	@Retry(name = "iaClientRetry")
	public IaCallResult call(ChatCompletionRequest req) throws IAException {
		String key = req.getApiKey();

		if (key == null || req.getModeloIA() == null) {
			throw new IllegalArgumentException("apiKey e modeloIA são obrigatórios no ChatCompletionRequest");
		}

		String modelName = req.getModeloIA().getModelo();

		WebClient client = webClient.mutate()
				.baseUrl(BASE_URL)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key)
				.build();

		var responseEntity = client.post()
				.bodyValue(Map.of(
					"model", modelName,
					"messages", req.getMessages(),
					"temperature", req.getTemperature(),
					"max_tokens", req.getMaxTokens()
				))
				.retrieve()
				.toEntity(ChatCompletionResponse.class)
				.block();

		if (responseEntity == null) {
			throw new IAException("Resposta nula da API OpenAI");
		}

		var body = responseEntity.getBody();
		if (body == null || body.getChoices() == null || body.getChoices().isEmpty()
			|| body.getChoices().get(0) == null || body.getChoices().get(0).getMessage() == null) {
			throw new IAException("Nenhuma resposta válida retornada pela OpenAI");
		}

		String resposta = body.getChoices().get(0).getMessage().getContent();
		HttpHeaders headers = responseEntity.getHeaders();
		String modelo = headers.getFirst("openai-model");

		return IaCallResult.builder()
				.resposta(resposta)
				.headers(headers)
				.modelo(modelo)
				.runId(null)
				.build();
	}


	public IaCallResult fallback(ChatCompletionRequest request, Throwable ex) {
		log.error("Erro na chamada OpenAI (fallback), request={}:", request, ex);
		return IaCallResult.builder()
				.resposta("Desculpe, estamos com instabilidade na API de IA. Tente novamente mais tarde.")
				.headers(new HttpHeaders())
				.modelo("fallback")
				.runId(null)
				.build();
	}
}
