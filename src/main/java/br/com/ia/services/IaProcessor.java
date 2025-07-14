package br.com.ia.services;

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

	private final IAClient iaClient;

	public IaProcessor(@Qualifier("router") IAClient iaClient) {
		this.iaClient = iaClient;
	}

	@Bean
	public Function<Message<IaRequest>, Message<IaResponse>> processIa() {
        return message -> {
			IaRequest req = message.getPayload();

			// Monta request para IA
			Map<String, Object> opts = req.getOptions() != null ? req.getOptions() : Map.of();
			String apiKey = (String) opts.getOrDefault("api_key", null);
			String model = (String) opts.getOrDefault("model", null);
			String assistantId = (String) opts.getOrDefault("assistantId", null);
			Double temperature = opts.containsKey("temperature") ? Double.valueOf(opts.get("temperature").toString()) : null;
			Integer maxTokens = opts.containsKey("max_tokens") ? Integer.valueOf(opts.get("max_tokens").toString()) : null;

			var chatReq = ChatCompletionRequest.builder()
				.apiKey(apiKey)
				.model(model)
				.assistantId(assistantId)
				.provider(req.getProvider()) 
				.messages(List.of(new ChatMessage("user", req.getPrompt())))
				.temperature(temperature)
				.maxTokens(maxTokens)
				.build();

			String resposta = iaClient.call(chatReq);

			return MessageBuilder
				.withPayload(new IaResponse(req.getCorrelationId(), resposta))
				.setHeader("correlationId", req.getCorrelationId())
				.build();
        };
	}
}
