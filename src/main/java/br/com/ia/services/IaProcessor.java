package br.com.ia.services;

import java.util.List;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import br.com.ia.config.IaProperties;
import br.com.ia.model.ChatCompletionRequest;
import br.com.ia.model.ChatMessage;
import br.com.ia.model.IaRequest;
import br.com.ia.model.IaResponse;

@Component
public class IaProcessor {

	private final IAClient openaiClient;
	private final IaProperties props;

	public IaProcessor(@Qualifier("openai") IAClient openaiClient, IaProperties props) {
		this.openaiClient = openaiClient;
		this.props = props;
	}

	@Bean
	public Function<Message<IaRequest>, Message<IaResponse>> processIa() {
		return message -> {
			IaRequest req = message.getPayload();

			ChatCompletionRequest chatReq = 
					ChatCompletionRequest
						.builder()
						.model(props.getOpenai().getModel())
					.messages(List.of(
							new ChatMessage("user", req.getPrompt())))
					.temperature(req.getOptions() != null && req.getOptions().get("temperature") != null
							? Double.parseDouble(req.getOptions().get("temperature").toString())
							: null)
					.maxTokens(req.getOptions() != null && req.getOptions().get("max_tokens") != null
							? (Integer) req.getOptions().get("max_tokens")
							: null)
					.build();

			String content = openaiClient.call(chatReq);

			IaResponse response = new IaResponse(req.getCorrelationId(), content);

			return MessageBuilder.withPayload(response).setHeader("correlationId", req.getCorrelationId()).build();
		};
	}
}
