package br.com.ia.processor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import br.com.ia.messaging.MessageType;
import br.com.ia.sdk.Base64MessageWrapper;
import br.com.ia.sdk.response.RespostaIA;
import br.com.ia.utils.ObjectToBytesConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IaReplyPublisher {

	private final KafkaTemplate<byte[], byte[]> kafkaTemplate;
	private final Base64MessageWrapper wrapper;

	@Value("${spring.cloud.stream.bindings.processIaConsumer-in-0.destination:ia.requests}")
	private String topic;

	public void sendWrapped(Object payload, MessageType type) {
		try {
			if (type == MessageType.IA_RESPONSE) {
				sendRawResponse((RespostaIA) payload);
			} else {
				String msg = wrapper.wrapToBase64(payload, type.name());
				sendRaw(msg);
			}
		} catch (Exception e) {
			log.error("[PUBLISHER] wrap/send error: {}", e.getMessage(), e);
		}
	}

	public void sendProcessingResponse(String status, String reason, String message, Object originalData) {
		Map<String, Object> resp = Map.of("status", status, "reason", reason, "message", message, "timestamp",
				System.currentTimeMillis(), "processor", "IaProcessor", "originalData",
				originalData != null ? originalData : Map.of());
		sendWrapped(resp, MessageType.IA_RESPONSE);
	}

	public void sendProcessingProcessed(String status, String reason, String message, Object originalData) {
		Map<String, Object> resp = Map.of("status", status, "reason", reason, "message", message, "timestamp",
				System.currentTimeMillis(), "processor", "IaProcessor", "originalData",
				originalData != null ? originalData : Map.of());
		sendWrapped(resp, MessageType.PROCESSED);
	}

	public void sendError(String reason, String message) {
		sendProcessingResponse("error", reason, message, null);
	}

	private void sendRawResponse(RespostaIA respostaIA) {
		try {
			kafkaTemplate.send(topic, "IA_RESPONSE".getBytes(StandardCharsets.UTF_8),
					ObjectToBytesConverter.objectToBytes(respostaIA)).whenComplete((res, ex) -> {
						if (ex != null) {
							log.error("[PUBLISHER] Failed to send to {}: {}", topic, ex.getMessage(), ex);
						} else {
							log.debug("[PUBLISHER] Sent to {} (p={}, o={})", topic, res.getRecordMetadata().partition(),
									res.getRecordMetadata().offset());
						}
					});
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	public void sendRaw(String msg) {
		kafkaTemplate.send(topic, msg.getBytes(StandardCharsets.UTF_8)).whenComplete((res, ex) -> {
			if (ex != null) {
				log.error("[PUBLISHER] Failed to send to {}: {}", topic, ex.getMessage(), ex);
			} else {
				log.debug("[PUBLISHER] Sent to {} (p={}, o={})", topic, res.getRecordMetadata().partition(),
						res.getRecordMetadata().offset());
			}
		});
	}
}
