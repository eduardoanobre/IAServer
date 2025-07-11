package br.com.ia.services.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;

import br.com.ia.config.IaProperties;
import br.com.ia.model.ChatCompletionRequest;
import br.com.ia.model.ChatCompletionResponse;
import br.com.ia.services.IAClient;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Qualifier("openai")
public class OpenAiClient implements IAClient {

    private final WebClient webClient;
    private final IaProperties props;

    public OpenAiClient(WebClient.Builder builder, IaProperties props) {
        this.props = props;
        this.webClient = builder
            .baseUrl(props.getOpenai().getUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getOpenai().getKey())
            .build();
    }

    @Override
    @CircuitBreaker(name = "iaClient", fallbackMethod = "fallback")
    @Retry(name = "iaClientRetry")
    public String call(ChatCompletionRequest req) {
        // Define modelo padrão se não informado
        if (req.getModel() == null || req.getModel().isEmpty()) {
            req.setModel(props.getOpenai().getModel());
        }
        return webClient.post()
            .bodyValue(req)
            .retrieve()
            .bodyToMono(ChatCompletionResponse.class)
            .map(response -> {
                if (response.getChoices().isEmpty()) {
                    throw new RuntimeException("Nenhuma escolha retornada pela API de IA");
                }
                return response.getChoices().get(0)
                               .getMessage()
                               .getContent();
            })
            .block();
    }

    public String fallback(ChatCompletionRequest request, Throwable ex) {
        log.error("Erro na chamada OpenAI, request={}:", request, ex);
        return "Desculpe, estamos com instabilidade na API de IA. Tente novamente mais tarde.";
    }
}