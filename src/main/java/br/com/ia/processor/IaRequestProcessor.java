package br.com.ia.processor;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import br.com.ia.messaging.MessageType;
import br.com.ia.sdk.context.entity.LogIA;
import br.com.ia.sdk.context.repository.LogIARepository;
import br.com.ia.sdk.response.RespostaIA;
import br.com.ia.utils.ObjectToBytesConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public abstract class IaRequestProcessor {

	private final IaMessageNormalizer normalizer;
	private final IaMessageClassifier classifier;
	private final IaRequestHandler iaHandler;
	private final IaReplyPublisher publisher;
	private final LogIARepository repository;

	@SuppressWarnings("unchecked")
	@KafkaListener(topics = "ia.requests", groupId = "ia-processor-v10")
	public void handleRequests(Object value, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
			@Header(KafkaHeaders.RECEIVED_PARTITION) int part, @Header(KafkaHeaders.OFFSET) long off,
			@Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {

		Map<String, Object> normalized = normalizer.normalize(value);
		MessageType type = classifier.identify(normalized);

		if (type == MessageType.PROCESSED) {
			log.warn("[IaRequestProcessor] Ignored {} to prevent loop/no-op", type);
			return;
		}

		if (type == MessageType.STARTUP_TEST) {
			publisher.sendProcessingProcessed("done", "startup_test", "Startup test processed successfully", value);
			return;
		}

		Map<String, Object> payload = (Map<String, Object>) normalized.get("payload");
		String requestId = payload.get("requestId").toString();

		log.info("[IaRequestProcessor] MESSAGE TYPE: {}", type);
		log.info("[IaRequestProcessor] MESSAGE ID: {}", requestId);

		if (type == MessageType.IA_REQUEST) {
			RespostaIA reply = iaHandler.handle(payload);
			publisher.sendWrapped(reply, MessageType.IA_RESPONSE);
			return;
		}

		if (type == MessageType.IA_RESPONSE) {
			RespostaIA respostaIA = null;
			try {
				respostaIA = (RespostaIA) ObjectToBytesConverter.base64ToObject(value.toString());
			} catch (ClassNotFoundException e) {
				log.error(e.getMessage());
				e.printStackTrace();
			} catch (IOException e) {
				log.error(e.getMessage());
				e.printStackTrace();
			}
			onSuccessfulIaResponse(respostaIA);
			publisher.sendWrapped(payload, MessageType.PROCESSED);
			return;
		}

		// nunca lance exceção por tipo não suportado; apenas logue
		log.warn("[IA-PROCESSOR] Ignorando mensagem type={}", type);
	}

	protected void onSuccessfulIaResponse(RespostaIA respostaIA) {
		LogIA logIA = repository.findById(Long.parseLong(respostaIA.id())).orElseThrow();

		BigDecimal custo = logIA.getCusto();
		if (custo == null) {
			custo = BigDecimal.ZERO;
		}
		custo = custo.add(respostaIA.custo());

		var fim = LocalDateTime.now();
		var duration = java.time.Duration.between(logIA.getData(), fim).toMillis();
		logIA.setDuracaoExecucaoMs(duration);
		logIA.setResposta(respostaIA.resposta());
		logIA.setCusto(custo);
		logIA.setErro(null);
		logIA.setErrorMessage(null);
		logIA.setSucesso(true);
		logIA.setTokensPrompt(respostaIA.tokensPrompt());
		logIA.setTokensResposta(respostaIA.tokensResposta());
		repository.save(logIA);
	}
}
