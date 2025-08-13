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
import br.com.ia.utils.CacheKeys;
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
		preValidacoes(r);
		
		Map<String, Object> opts = new HashMap<>();
		opts.put("model", r.getModel());
		if (!isBlank(r.getInstructions())) opts.put("instructions", r.getInstructions());
		if (r.getText() != null) opts.put("text", r.getText());
		if (r.getMaxOutputTokens() != null) opts.put("max_output_tokens", r.getMaxOutputTokens());

		// temperatura (0..100 -> 0..2)
		double temp = Temperature.fromPercent(r.getTemperaturePercent(), Temperature.DEFAULT);
		opts.put("temperature", temp);

		// segurança/telemetria
		opts.put("safety_identifier", r.getChatId());

		// shards -> lista reduzida para a chave de cache
		var shardList = new java.util.ArrayList<java.util.Map<String,Object>>();
		if (r.getContextShards() != null && !r.getContextShards().isEmpty()) {
		  // também encaminhe os shards completos para a IA (se você já faz isso em outro lugar, ok)
		  var fullShardList = new java.util.ArrayList<java.util.Map<String,Object>>();

		  for (var s : r.getContextShards()) {
		    // para compose(...)
		    shardList.add(Map.of(
		      "type", s.type(),
		      "version", s.version()
		    ));
		    // para a chamada à IA (completa)
		    fullShardList.add(Map.of(
		      "type", s.type(),
		      "version", s.version(),
		      "stable", s.stable(),
		      "payload", s.payload()
		    ));
		  }
		  opts.put("context_shards", fullShardList);
		}

		String moduleKey = (r.getModuleKey() == null || r.getModuleKey().isBlank())
		    ? "generic" : r.getModuleKey();
		Integer rulesV  = (r.getVersaoRegrasModulo() == null ? 1 : r.getVersaoRegrasModulo());
		Integer schemaV = (r.getVersaoSchema()        == null ? 1 : r.getVersaoSchema());

		String cacheKey = CacheKeys.compose(
		    r.getChatId(),
		    moduleKey,
		    rulesV,
		    schemaV,
		    shardList,
		    r.getCacheFacet()
		);
		opts.put("prompt_cache_key", cacheKey);
		
		opts.put("metadata", Map.of(
				  "chatId", r.getChatId(),
				  "moduleKey", moduleKey
				));

		IaRequest iaReq = provider.getRequest(r.getPrompt(), r.getChatId(), r.getApiKey(), opts);		
		
		var future = pending.create(r.getChatId());
		Message<IaRequest> msg = MessageBuilder.withPayload(iaReq).setHeader("chatId", r.getChatId())
				.setHeader(KafkaHeaders.KEY, r.getChatId()).build();

		bridge.send(topic, msg);

		return IAUtils.aguardarRespostaIA(r.getChatId(), future, pending, Duration.ofMillis(timeoutMs), true);
	}

	private void preValidacoes(PromptRequest r) throws IAException {
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
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
