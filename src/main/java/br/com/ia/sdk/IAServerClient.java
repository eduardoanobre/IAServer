package br.com.ia.sdk;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ia.model.IaRequest;
import br.com.ia.sdk.context.repository.LogIARepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Component
@Slf4j
public class IAServerClient {

	private final ObjectMapper objectMapper;
	private final LogIARepository repository;
	private final KafkaTemplate<byte[], byte[]> kafkaTemplate;

	@Value("${spring.cloud.stream.bindings.processIaConsumer-in-0.destination:ia.requests}")
	private String requestsTopic;

	@Value("${ia.module.name}")
	private String moduleName;

	/**
	 * Sends IA Request to IAServer synchronously
	 */
	public boolean sendIaRequest(IaRequest request) {
		try {
			log.info("[SEND-IA-REQUEST] Starting - ChatId: {}", request.getChatId());
			log.info("[SEND-IA-REQUEST] Starting - Id: {}", request.getRequestId());

			if (!isValidIaRequest(request)) {
				log.error("[SEND-IA-REQUEST] Invalid request for ChatId: {}", request.getChatId());
				return false;
			}

			adjustSafeOptions(request);
			log.debug("[SEND-IA-REQUEST] Request validation and adjustment completed");

			// Wrap message in Base64 envelope
			String base64Message = wrapMessageToBase64(request, "IA_REQUEST");
			log.debug("[SEND-IA-REQUEST] Base64 wrapping completed - Length: {} chars", base64Message.length());

			// Send message using KafkaTemplate
			boolean success = sendMessage(base64Message, request.getChatId());

			if (success) {
				log.info("[SEND-IA-REQUEST] SUCCESS - Message sent for Id: {}", request.getRequestId());
				repository.setEnviado(request.getRequestId());
			} else {
				log.error("[SEND-IA-REQUEST] FAILED - Could not send message for ChatId: {}", request.getChatId());
			}

			return success;

		} catch (Exception e) {
			log.error("[SEND-IA-REQUEST] EXCEPTION - ChatId: {}, Error: {}", request.getChatId(), e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Send message using KafkaTemplate only
	 */
	private boolean sendMessage(String string, String chatId) {
		try {
			log.debug("[KAFKA-SEND] Sending message for ChatId: {}", chatId);

			// Synchronous send with blocking call
			SendResult<byte[], byte[]> result = kafkaTemplate.send(requestsTopic,
					chatId.getBytes(StandardCharsets.UTF_8), string.getBytes(StandardCharsets.UTF_8)).get();

			log.info("[KAFKA-SEND] SUCCESS - ChatId: {}, Partition: {}, Offset: {}", chatId,
					result.getRecordMetadata().partition(), result.getRecordMetadata().offset());

			return true;

		} catch (InterruptedException e) {
			log.error("[KAFKA-SEND] FAILED - ChatId: {}, Error: {}", chatId, e.getMessage());
			log.debug("[KAFKA-SEND] Exception details:", e);
			Thread.currentThread().interrupt();
			return false;
		} catch (Exception e) {
			log.error("[KAFKA-SEND] FAILED - ChatId: {}, Error: {}", chatId, e.getMessage());
			log.debug("[KAFKA-SEND] Exception details:", e);
			return false;
		}
	}

	/**
	 * Wraps any message in Base64 envelope
	 */
	private String wrapMessageToBase64(Object message, String messageType) throws Exception {
		log.debug("[WRAP-BASE64] Type: {}, Base64 enabled: {}", messageType);

		Map<String, Object> envelope = Map.of("type", messageType, "timestamp", System.currentTimeMillis(), "module",
				moduleName, "payload", message);

		String json = objectMapper.writeValueAsString(envelope);
		log.debug("[WRAP-BASE64] JSON length: {} chars", json.length());

		String base64String = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
		log.debug("[WRAP-BASE64] Base64 length: {} chars", base64String.length());

		return base64String;
	}

	/**
	 * Validates IaRequest before sending
	 */
	private boolean isValidIaRequest(IaRequest request) {
		if (request == null) {
			log.error("[VALIDATION] IaRequest is null");
			return false;
		}

		if (request.getChatId() == null || request.getChatId().trim().isEmpty()) {
			log.error("[VALIDATION] ChatId is null or empty");
			return false;
		}

		if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
			log.error("[VALIDATION] Prompt is null or empty for ChatId: {}", request.getChatId());
			return false;
		}

		if (request.getOptions() == null) {
			log.error("[VALIDATION] Options are null for ChatId: {}", request.getChatId());
			return false;
		}

		// Check for API key
		if (!request.getOptions().containsKey(IaRequest.API_KEY)) {
			log.error("[VALIDATION] API key is missing in options for ChatId: {}", request.getChatId());
			return false;
		}

		// Check for required model field
		if (!request.getOptions().containsKey(IaRequest.MODEL)) {
			log.error("[VALIDATION] Model is missing in options for ChatId: {}", request.getChatId());
			return false;
		}

		// Reject test chatIds for regular requests
		if (request.getChatId().startsWith("test-")) {
			log.warn("[VALIDATION] Rejecting test chatId for regular request: {}", request.getChatId());
			return false;
		}

		log.debug("[VALIDATION] Request validated successfully for ChatId: {}", request.getChatId());
		return true;
	}

	/**
	 * Adjusts and validates request options for safety
	 */
	private void adjustSafeOptions(IaRequest request) {
		if (request.getOptions() == null) {
			request.setOptions(new HashMap<>());
		}

		Map<String, Object> options = request.getOptions();

		// Safely get and adjust max output tokens
		Object maxTokensObj = options.get(IaRequest.MAX_OUTPUT_TOKENS);
		int maxOutputTokens = safeTokens(maxTokensObj instanceof Number value ? value.intValue() : 1024);

		// Safely get and adjust temperature
		Object temperatureObj = options.get(IaRequest.TEMPERATURE);
		double temperature = safeTemperature(temperatureObj instanceof Number value ? value.doubleValue() : 0.3);

		// Update options with safe values
		Map<String, Object> correctedOptions = new HashMap<>(options);
		correctedOptions.put(IaRequest.MAX_OUTPUT_TOKENS, maxOutputTokens);
		correctedOptions.put(IaRequest.TEMPERATURE, temperature);

		request.setOptions(correctedOptions);

		log.debug("[ADJUST-OPTIONS] ChatId: {}, Tokens: {}, Temperature: {}", request.getChatId(), maxOutputTokens,
				temperature);
	}

	private int safeTokens(Object tokensObj) {
		int tokens = 1024;
		if (tokensObj instanceof Number value) {
			tokens = value.intValue();
		}
		if (tokens <= 0)
			tokens = 1024;
		if (tokens > 4096)
			tokens = 4096;
		return tokens;
	}

	private double safeTemperature(Object temperatureObj) {
		double temp = 0.3;
		if (temperatureObj instanceof Number value) {
			temp = value.doubleValue();
		}
		if (temp < 0)
			temp = 0.0;
		if (temp > 2.0)
			temp = 2.0;
		return temp;
	}
}