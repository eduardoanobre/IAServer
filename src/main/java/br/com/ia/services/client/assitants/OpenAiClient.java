package br.com.ia.services.client.assitants;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import br.com.ia.model.ChatCompletionRequest;
import br.com.ia.model.ChatCompletionResponse;
import br.com.ia.services.client.IAIClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OpenAiClient implements IAIClient {

	static WebClient webClient = WebClient.create();

    @Override
    @CircuitBreaker(name = "iaClient", fallbackMethod = "fallback")
    @Retry(name = "iaClientRetry")
    public String call(ChatCompletionRequest req) {
        String key = req.getApiKey();
        
        if (key == null || req.getModel() == null) {
            throw new IllegalArgumentException("URL, apiKey and model must be provided in ChatCompletionRequest");
        }
        
        WebClient client = webClient.mutate()
                .baseUrl("https://api.openai.com/v1/chat/completions")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                .build();

            ChatCompletionResponse resp = client.post()
                .bodyValue(req)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .block();

            if (resp == null || resp.getChoices() == null || resp.getChoices().isEmpty()) {
                throw new RuntimeException("Nenhuma escolha retornada pela API de IA"); //NOSONAR
            }
            return resp.getChoices().get(0).getMessage().getContent();
    }

    public String fallback(ChatCompletionRequest request, Throwable ex) {
        log.error("Erro na chamada OpenAI, request={}:", request, ex);
        return "Desculpe, estamos com instabilidade na API de IA. Tente novamente mais tarde.";
    }
}