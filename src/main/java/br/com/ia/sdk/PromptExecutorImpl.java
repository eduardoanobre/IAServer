package br.com.ia.sdk;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import br.com.ia.model.IaRequest;
import br.com.ia.model.IaResponse;
import br.com.ia.model.RequestProvider;
import br.com.ia.services.PendingIaRequestStore;
import br.com.ia.utils.IAUtils;
import br.com.shared.exception.IAException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PromptExecutorImpl implements PromptExecutor {

	private final StreamBridge bridge;
	private final RequestProvider provider;
	private final PendingIaRequestStore pending;

	@Value("${ia.responses.topic:processIa-in-0}")
	private String topic;

	@Value("${erp.ia.reply-timeout-ms:30000}")
	private long timeoutMs;

	@Override
	public IaResponse executaPrompt(PromptRequest r) throws IAException {
		if (r == null)
			throw new IAException("PromptRequest nulo");
		if (isBlank(r.getChatId()))
			throw new IAException("chatId obrigatório");
		if (isBlank(r.getPrompt()))
			throw new IAException("prompt obrigatório");
		if (isBlank(r.getApiKey()))
			throw new IAException("apiKey obrigatório");
		if (isBlank(r.getModel()))
			throw new IAException("model obrigatório");
		int instrVer = (r.getVersaoInstrucao() != null ? r.getVersaoInstrucao() : 1);
		int schemaVer = (r.getVersaoSchema() != null ? r.getVersaoSchema() : 1);

		// monta options Responses API
		Map<String, Object> opts = new HashMap<>();
		opts.put("model", r.getModel());
		if (!isBlank(r.getInstructions()))
			opts.put("instructions", r.getInstructions());
		if (r.getText() != null)
			opts.put("text", r.getText());
		if (r.getMaxOutputTokens() != null)
			opts.put("max_output_tokens", r.getMaxOutputTokens());

		// temp: 0..100 -> 0..2
		double temp = Temperature.fromPercent(r.getTemperaturePercent(), Temperature.DEFAULT);
		opts.put("temperature", temp);

		// cache key calculada: chatId + versaoInstrucao (fallback = 1)
		String cacheKey = CacheKeys.forProjeto(r.getChatId(), instrVer, schemaVer);
		opts.put("prompt_cache_key", cacheKey);

		// segurança/telemetria
		opts.put("safety_identifier", r.getChatId());

		// constrói IaRequest, envia e aguarda
		IaRequest iaReq = provider.getRequest(r.getPrompt(), r.getChatId(), r.getApiKey(), opts);

		var future = pending.create(r.getChatId());
		Message<IaRequest> msg = MessageBuilder.withPayload(iaReq).setHeader("chatId", r.getChatId())
				.setHeader(KafkaHeaders.KEY, r.getChatId()).build();

		bridge.send(topic, msg);

		return IAUtils.aguardarRespostaIA(r.getChatId(), future, pending, Duration.ofMillis(timeoutMs), true);
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
