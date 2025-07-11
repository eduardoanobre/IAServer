package br.com.ia.services.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import br.com.ia.model.ChatCompletionRequest;
import br.com.ia.model.ChatCompletionResponse;
import br.com.ia.services.IAClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Qualifier("openai")
public class OpenAiClient implements IAClient {

    private final WebClient webClient;
    private static final String URL = "https://api.openai.com/v1/chat/completions";

    public OpenAiClient(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @Override
    @CircuitBreaker(name = "iaClient", fallbackMethod = "fallback")
    @Retry(name = "iaClientRetry")
    public String call(ChatCompletionRequest req) {
        String url = URL;
        String key = req.getApiKey();
        
        if (key == null || req.getModel() == null) {
            throw new IllegalArgumentException("URL, apiKey and model must be provided in ChatCompletionRequest");
        }
        
        WebClient client = webClient.mutate()
                .baseUrl(url)
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