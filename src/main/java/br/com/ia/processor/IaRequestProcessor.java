package br.com.ia.processor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import br.com.ia.messaging.MessageType;
import br.com.ia.publisher.IaPublisher;
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
	private final IaRequestHandler iaHandler;
	private final IaPublisher publisher;
	private final LogIARepository repository;

	@SuppressWarnings("unchecked")
	@KafkaListener(topics = "ia.requests", groupId = "ia-processor-v10")
	public void handleRequests(ConsumerRecord<?, ?> value, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
			@Header(KafkaHeaders.RECEIVED_PARTITION) int part, @Header(KafkaHeaders.OFFSET) long off,
			@Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
			@Header("message-type") String messageType) {

		MessageType type = MessageType.valueOf(messageType);

		if (type == MessageType.PROCESSED) {
			log.warn("[IaRequestProcessor] Ignored {} to prevent loop/no-op", type);
			return;
		}

		if (type == MessageType.STARTUP_TEST) {
			publisher.publish(MessageType.PROCESSED, "Startup test processed successfully", "0");
			return;
		}

		String chatId = obterChatId(value);

		log.info("[IaRequestProcessor] MESSAGE TYPE: {}", type);
		log.info("[IaRequestProcessor] MESSAGE chatId: {}", chatId);

		if (type == MessageType.IA_REQUEST) {
			RespostaIA reply = iaHandler.handle(value.value());
			publisher.publish(MessageType.IA_RESPONSE, reply, chatId);
			return;
		}

		if (type == MessageType.IA_RESPONSE) {
			RespostaIA resposta = obterRespostaIa(value);
			onSuccessfulIaResponse(resposta);
			publisher.publish(MessageType.PROCESSED, value, chatId);
			return;
		}

		throw new IllegalArgumentException("tipo de mensagem inválida");
	}

	private RespostaIA obterRespostaIa(ConsumerRecord<?, ?> value) {
		try {
			return ObjectToBytesConverter.bytesToObject((byte[]) value.value(), RespostaIA.class);
		} catch (Exception e) {
			return new RespostaIA("", null, e.getMessage(), 0, 0, null, null, null, null);
		}
	}

	private String obterChatId(Object value) {
		try {
			@SuppressWarnings("rawtypes")
			byte[] bytes = (byte[]) ((ConsumerRecord) value).key();
			return new String(bytes, StandardCharsets.UTF_8);
		} catch (Exception ignore) {
			return "CHAT_ID_NAO_IDENTIFICADO";
		}
	}

	protected void onSuccessfulIaResponse(RespostaIA respostaIA, String resposta) {
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
		logIA.setResposta(resposta);
		repository.save(logIA);
	}
}
