package br.com.ia.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import br.com.ia.model.ChatCompletionRequest;
import br.com.ia.model.ChatMessage;
import br.com.ia.model.IaRequest;
import br.com.ia.model.IaResponse;
import br.com.ia.services.client.IAIClient;
import br.com.ia.services.client.IaCallResult;
import br.com.ia.services.client.assitants.OpenAiAssistantsClient;
import br.com.ia.services.client.assitants.OpenAiClient;
import br.com.ia.utils.OpenAICustoUtil;
import br.com.shared.model.enums.EnumModeloIA;

@Component
public class IaProcessor {

	@Bean
	public Function<Message<IaRequest>, Message<IaResponse>> processIa() {
		return message -> {
			try {
				IaRequest req = message.getPayload();
				EnumModeloIA modeloIA = req.getModeloIA();

				if (modeloIA == null) {
					throw new IllegalArgumentException("Modelo de IA não informado.");
				}

				// Extração de opções
				Map<String, Object> opts = req.getOptions() != null ? req.getOptions() : Map.of();
				String apiKey = (String) opts.getOrDefault("api_key", null);
				String assistantId = (String) opts.getOrDefault("assistantId", null);
				Double temperature = opts.containsKey("temperature") ? Double.valueOf(opts.get("temperature").toString()) : null;
				Integer maxTokens = opts.containsKey("max_tokens") ? Integer.valueOf(opts.get("max_tokens").toString()) : null;

				// Seleciona client com base na IA associada ao modelo
				IAIClient iaClient = switch (modeloIA.getIa()) {
					case CHATGPT -> new OpenAiClient();
					case CHATGPT_ASSISTANTS -> new OpenAiAssistantsClient();
					default -> throw new IllegalArgumentException("IA provider desconhecido: " + modeloIA.getIa());
				};

				// Monta request
				var chatReq = ChatCompletionRequest.builder()
					.apiKey(apiKey)
					.modeloIA(modeloIA)
					.assistantId(assistantId)
					.messages(List.of(new ChatMessage("user", req.getPrompt())))
					.temperature(temperature)
					.maxTokens(maxTokens)
					.build();

				IaCallResult result = iaClient.call(chatReq);

				HttpHeaders headers = result.getHeaders();
				BigDecimal custo = OpenAICustoUtil.calcularCustoPorHeaders(headers);
				String modelo = result.getModelo();
				int tokensPrompt = Integer.parseInt(headers.getFirst("openai-usage-tokens-prompt"));
				int tokensResposta = Integer.parseInt(headers.getFirst("openai-usage-tokens-completion"));

				IaResponse iaResponse = new IaResponse(
					req.getCorrelationId(),
					result.getResposta(),
					custo,
					modelo,
					tokensPrompt,
					tokensResposta
				);

				return MessageBuilder
						.withPayload(iaResponse)
						.setHeader("correlationId", req.getCorrelationId())
						.build();

			} catch (Exception e) {
				IaResponse erro = new IaResponse(
					message.getPayload().getCorrelationId(),
					"Erro ao processar IA: " + e.getMessage(),
					BigDecimal.ZERO,
					"erro",
					0,
					0
				);
				return MessageBuilder.withPayload(erro).build();
			}
		};	}

}
