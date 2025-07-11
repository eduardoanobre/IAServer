package br.com.ia.services;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import br.com.ia.model.ChatCompletionRequest;
import br.com.ia.model.ChatMessage;
import br.com.ia.model.IaRequest;
import br.com.ia.model.IaResponse;

@Component
public class IaProcessor {

	private final IAClient openaiClient;

	public IaProcessor(@Qualifier("openai") IAClient openaiClient) {
		this.openaiClient = openaiClient;
	}

	@Bean
	public Function<Message<IaRequest>, Message<IaResponse>> processIa() {
        return message -> {
            IaRequest req = message.getPayload();

            // Extrai configurações do payload
            Map<String, Object> opts = req.getOptions() != null ? req.getOptions() : Collections.emptyMap();
            String apiKeyOverride = opts.containsKey("api_key")
                ? opts.get("api_key").toString()
                : null;
            String modelOverride = opts.containsKey("model")
                ? opts.get("model").toString()
                : null;
            Double temperature = opts.containsKey("temperature")
                ? Double.parseDouble(opts.get("temperature").toString())
                : null;
            Integer maxTokens = opts.containsKey("max_tokens")
                ? (Integer) opts.get("max_tokens")
                : null;

            // Monta o request para a IA
            ChatCompletionRequest chatReq = ChatCompletionRequest.builder()
                .apiKey(apiKeyOverride)
                .model(modelOverride)
                .messages(List.of(new ChatMessage("user", req.getPrompt())))
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

            // Chama o client e empacota a resposta
            String content = openaiClient.call(chatReq);
            IaResponse response = new IaResponse(req.getCorrelationId(), content);

            return MessageBuilder.withPayload(response)
                .setHeader("correlationId", req.getCorrelationId())
                .build();
        };
	}
}
