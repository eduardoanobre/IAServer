package br.com.ia.processor;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.ia.kafka.KafkaDebug;
import br.com.ia.messaging.MessageType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class IaMessageClassifier {

	private static final Set<String> RESPONSE_SIGNATURE_FIELDS = Set.of("resposta", "custo", "modelo", "tokensPrompt",
			"tokensResposta", "success", "errorMessage", "timestamp");

	private static final Set<String> IGNORED_ENVELOPE_TYPES = Set.of("IA_RESPONSE", "PROCESSING_RESPONSE",
			"WORKSPACE_RESPONSE");

	public MessageType identify(Map<String, Object> input) {
		String envelopeType = (String) input.get("type");
		if (envelopeType != null) {
			log.debug("[CLASSIFIER] Found envelope type: {}", envelopeType);

			if (IGNORED_ENVELOPE_TYPES.contains(envelopeType)) {
				log.warn("[CLASSIFIER] {} detected – prevent loop", envelopeType);
				return MessageType.IA_RESPONSE_LOOP;
			}

			Object p = input.get("payload");
			if (p instanceof Map<?, ?> payload) {
				return identifyFromPayload(cast(payload));
			}
			return mapEnvelopeType(envelopeType);
		}
		return identifyFromPayload(input);
	}

	private MessageType identifyFromPayload(Map<String, Object> payload) {
		log.debug("[CLASSIFIER] Analyzing payload keys: {}", payload.keySet());

		if (hasResponseSignature(payload)) {
			log.warn("[CLASSIFIER] Response signature detected – loop prevention");
			return MessageType.IA_RESPONSE_LOOP;
		}

		String source = String.valueOf(payload.getOrDefault("source", ""));
		if (source.contains("startup") || "workspace-startup".equals(source)
				|| KafkaDebug.WORKSPACE_STARTUP.equals(source)) {
			return MessageType.STARTUP_TEST;
		}

		String chatId = String.valueOf(payload.getOrDefault("chatId", ""));
		if (chatId.startsWith("test-") || payload.containsKey("test") || source.contains("connection-test")) {
			return MessageType.CONNECTION_TEST;
		}

		if (hasValidIaRequestStructure(payload)) {
			return MessageType.IA_REQUEST;
		}

		return MessageType.UNKNOWN;
	}

	private boolean hasResponseSignature(Map<String, Object> input) {
		long responseFieldCount = input.keySet().stream().filter(RESPONSE_SIGNATURE_FIELDS::contains).count();
		return responseFieldCount >= 2;
	}

	private boolean hasValidIaRequestStructure(Map<String, Object> input) {
		String chatId = String.valueOf(input.getOrDefault("chatId", ""));
		String prompt = String.valueOf(input.getOrDefault("prompt", ""));
		return !chatId.isBlank() && !prompt.isBlank() && !chatId.startsWith("test-") && input.containsKey("options");
	}

	private MessageType mapEnvelopeType(String envelopeType) {
		return switch (envelopeType) {
		case "IA_REQUEST" -> MessageType.IA_REQUEST;
		case "STARTUP_TEST" -> MessageType.STARTUP_TEST;
		case "CONNECTION_TEST" -> MessageType.CONNECTION_TEST;
		default -> MessageType.UNKNOWN;
		};
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> cast(Object o) {
		return (Map<String, Object>) o;
	}
}
