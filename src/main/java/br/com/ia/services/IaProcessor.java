package br.com.ia.services;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ia.config.IaKafkaConfiguration;
import br.com.ia.kafka.KafkaDebug;
import br.com.ia.model.IaRequest;
import br.com.ia.model.IaResponse;
import br.com.ia.model.responses.ResponsesRequest;
import br.com.ia.model.responses.ResponsesResponse;
import br.com.ia.sdk.Base64MessageWrapper;
import br.com.ia.services.client.responses.ResponsesClient;
import br.com.ia.utils.OpenAICustoUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * IA PROCESSOR - Consumer Mode with Manual Producer
 * 
 * FLUXO PRINCIPAL: 1. Consumer recebe mensagem do tópico ia.requests 2.
 * Normalização → Identificação de Tipo → Processamento 3. Se IA_REQUEST válido
 * → Processa → Envia para ia.responses via KafkaTemplate 4. Se
 * loop/duplicate/test → Ignora ou processa conforme necessário
 * 
 * ANTI-LOOP GARANTIDO: - Consumer apenas recebe (não retorna automaticamente) -
 * Producer manual controla exatamente quando enviar - Detecção robusta de
 * mensagens de resposta - Cache de mensagens processadas
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IaProcessor {

	private static final double DEFAULT_TEMP = 0.3;
	private static final String CHAT_ID = "chatId";
	private static final String TEST_CHAT_ID_PREFIX = "test-";
	private final IaKafkaConfiguration.IaStreamProperties iaStreamProperties;

	// ANTI-LOOP: Cache de mensagens processadas recentemente
	private final Set<String> processedMessageHashes = ConcurrentHashMap.newKeySet();

	// ANTI-LOOP: Campos que indicam estrutura de response
	private static final Set<String> RESPONSE_SIGNATURE_FIELDS = Set.of("resposta", "custo", "modelo", "tokensPrompt",
			"tokensResposta", "success", "errorMessage", "timestamp");

	// ANTI-LOOP: Tipos de envelope que devem ser ignorados
	private static final Set<String> IGNORED_ENVELOPE_TYPES = Set.of("IA_RESPONSE", "PROCESSING_RESPONSE",
			"WORKSPACE_RESPONSE");

	private final ResponsesClient responsesClient;
	private final ObjectMapper mapper;
	private final Base64MessageWrapper messageWrapper;
	private final KafkaTemplate<String, String> kafkaTemplate;

	@PostConstruct
	public void init() {
		log.info("╔════════════════════════════════════════════════════════════════╗");
		log.info("║                 IA PROCESSOR - CONSUMER MODE                   ║");
		log.info("╠════════════════════════════════════════════════════════════════╣");
		log.info("║ INPUT:  ia.requests  (Consumer)                                ║");
		log.info("║ OUTPUT: ia.responses (KafkaTemplate)                           ║");
		log.info("╚════════════════════════════════════════════════════════════════╝");
	}

	/**
	 * Spring Cloud Stream Consumer Bean Apenas recebe mensagens - não retorna nada
	 * automaticamente
	 */
	@Bean
	public Consumer<Object> processIaConsumer() {
		log.info("[IA-PROCESSOR] Consumer processIaConsumer registered!");

		return input -> {
			try {
				long startTime = System.currentTimeMillis();
				log.info("[IA-PROCESSOR] PROCESSING INPUT - Type: {}",
						input != null ? input.getClass().getSimpleName() : "null");

				// Processa a mensagem
				String result = processMessage(input);

				// Envia resposta apenas se não for null
				if (result != null) {
					sendResponse(result);
				} else {
					log.debug("[IA-PROCESSOR] No response to send (null result)");
				}

				long duration = System.currentTimeMillis() - startTime;
				log.info("[IA-PROCESSOR] Processing completed in {}ms", duration);

			} catch (Exception e) {
				log.error("[IA-PROCESSOR] CRITICAL EXCEPTION: {}", e.getMessage(), e);
				sendErrorResponse("processing_exception", e.getMessage());
			}
		};
	}

	/**
	 * Processa mensagem e retorna resposta em String Base64 ou null se deve ignorar
	 */
	private String processMessage(Object input) {
		try {
			// STEP 0: EARLY DUPLICATE DETECTION
			String messageHash = generateMessageHash(input);
			if (isDuplicateMessage(messageHash)) {
				log.warn("[IA-PROCESSOR] DUPLICATE MESSAGE DETECTED - IGNORING");
				return null; // Não envia resposta para duplicatas
			}

			// STEP 1: NORMALIZATION
			Map<String, Object> normalizedInput = normalizeInput(input);
			if (normalizedInput == null) {
				log.warn("[IA-PROCESSOR] Input normalization failed");
				return createErrorResponse("normalization_failed", "Could not normalize input");
			}

			// STEP 2: MESSAGE TYPE IDENTIFICATION
			MessageType messageType = identifyMessageType(normalizedInput);
			log.info("[IA-PROCESSOR] MESSAGE TYPE: {}", messageType);

			// STEP 3: ROUTING
			String result = routeMessageForConsumer(messageType, normalizedInput);

			// STEP 4: MARK AS PROCESSED (only for valid requests)
			if (messageType == MessageType.IA_REQUEST && result != null) {
				markAsProcessed(messageHash);
			}

			return result;

		} catch (Exception e) {
			log.error("[IA-PROCESSOR] Error processing message: {}", e.getMessage(), e);
			return createErrorResponse("processing_exception", e.getMessage());
		}
	}

	/**
	 * ENHANCED MESSAGE TYPE ENUMERATION
	 */
	private enum MessageType {
		IA_REQUEST, // Valid IA request to process
		IA_RESPONSE_LOOP, // Response message - IGNORE to prevent loop
		STARTUP_TEST, // Application startup test
		CONNECTION_TEST, // Connection test message
		PROCESSING_RESPONSE, // Internal processing response
		DUPLICATE_MESSAGE, // Already processed message
		UNKNOWN // Unrecognized format
	}

	/**
	 * MESSAGE ROUTING PARA CONSUMER - retorna String ou null
	 */
	private String routeMessageForConsumer(MessageType messageType, Map<String, Object> input) {
		return switch (messageType) {
		case STARTUP_TEST -> handleStartupTestConsumer(input);
		case CONNECTION_TEST -> handleConnectionTestConsumer(input);
		case IA_REQUEST -> handleIaRequestConsumer(input);
		case IA_RESPONSE_LOOP -> {
			log.warn("[IA-PROCESSOR] IA RESPONSE LOOP DETECTED - IGNORING TO PREVENT CYCLE");
			yield null; // Não responde para prevenir loop
		}
		case PROCESSING_RESPONSE -> {
			log.warn("[IA-PROCESSOR] PROCESSING RESPONSE DETECTED - IGNORING");
			yield null; // Não responde
		}
		case DUPLICATE_MESSAGE -> {
			log.warn("[IA-PROCESSOR] DUPLICATE MESSAGE DETECTED - IGNORING");
			yield null; // Não responde
		}
		case UNKNOWN -> handleUnknownMessageConsumer(input);
		};
	}

	/**
	 * CONSUMER HANDLERS - retornam String ou null
	 */
	private String handleStartupTestConsumer(Map<String, Object> input) {
		log.info("[IA-PROCESSOR] PROCESSING STARTUP TEST");
		return createIgnoredResponse("startup_test", "Startup test processed successfully");
	}

	private String handleConnectionTestConsumer(Map<String, Object> input) {
		log.info("[IA-PROCESSOR] PROCESSING CONNECTION TEST");
		return createIgnoredResponse("connection_test", "Connection test processed successfully");
	}

	private String handleIaRequestConsumer(Map<String, Object> input) {
		try {
			log.info("[IA-PROCESSOR] PROCESSING IA REQUEST");

			// Extract payload if wrapped
			Map<String, Object> requestData = input;
			if (input.containsKey("payload") && input.get("payload") instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> payload = (Map<String, Object>) input.get("payload");
				requestData = payload;
				log.debug("[IA-PROCESSOR] Extracted payload from envelope");
			}

			IaRequest request = mapper.convertValue(requestData, IaRequest.class);
			log.info("[IA-PROCESSOR] Processing request - ChatId: {}", request.getChatId());

			IaResponse response = processIaRequest(request);

			if (response != null) {
				log.info("[IA-PROCESSOR] IA PROCESSING COMPLETED - WRAPPING RESPONSE");
				return messageWrapper.wrapToBase64(response, "IA_RESPONSE");
			} else {
				log.warn("[IA-PROCESSOR] IA processing returned null response");
				return null; // Não envia resposta se processamento retornou null
			}

		} catch (Exception e) {
			log.error("[IA-PROCESSOR] Error processing IA request: {}", e.getMessage(), e);
			return createErrorResponse("ia_processing_failed", e.getMessage());
		}
	}

	private String handleUnknownMessageConsumer(Map<String, Object> input) {
		log.warn("[IA-PROCESSOR] UNKNOWN MESSAGE TYPE - Keys: {}", input.keySet());
		return createIgnoredResponse("unknown_format", "Unrecognized message format");
	}

	/**
	 * KAFKA PRODUCER METHODS
	 */
	private void sendResponse(String response) {
		try {
			String outputTopic = iaStreamProperties.getOutputDestination();
			kafkaTemplate.send(outputTopic, response).whenComplete((result, ex) -> {
				if (ex != null) {
					log.error("[IA-PRODUCER] Failed to send response: {}", ex.getMessage());
				} else {
					log.debug("[IA-PRODUCER] Response sent successfully to {}", outputTopic);
				}
			});
		} catch (Exception e) {
			log.error("[IA-PRODUCER] Error sending response: {}", e.getMessage(), e);
		}
	}

	private void sendErrorResponse(String reason, String message) {
		try {
			String errorResponse = createErrorResponse(reason, message);
			sendResponse(errorResponse);
		} catch (Exception e) {
			log.error("[IA-PRODUCER] Error sending error response: {}", e.getMessage(), e);
		}
	}

	/**
	 * RESPONSE CREATION HELPERS
	 */
	private String createIgnoredResponse(String reason, String message) {
		return createStandardResponse("ignored", reason, message, null);
	}

	private String createErrorResponse(String reason, String message) {
		return createStandardResponse("error", reason, message, null);
	}

	private String createStandardResponse(String status, String reason, String message, Object originalData) {
		Map<String, Object> response = Map.of("status", status, "reason", reason, "message", message, "timestamp",
				System.currentTimeMillis(), "processor", "IaProcessor", "originalData",
				originalData != null ? originalData : Map.of());

		log.debug("[IA-PROCESSOR] Creating response - Status: {}, Reason: {}", status, reason);
		return messageWrapper.wrapToBase64(response, "PROCESSING_RESPONSE");
	}

	/**
	 * DUPLICATE DETECTION
	 */
	private String generateMessageHash(Object input) {
		try {
			String inputStr = input.toString();
			if (inputStr.length() > 100) {
				inputStr = inputStr.substring(0, 100);
			}
			return Integer.toHexString(inputStr.hashCode());
		} catch (Exception e) {
			return "hash-error-" + System.nanoTime();
		}
	}

	private boolean isDuplicateMessage(String messageHash) {
		boolean isDuplicate = processedMessageHashes.contains(messageHash);
		if (!isDuplicate && processedMessageHashes.size() > 1000) {
			processedMessageHashes.clear();
			log.debug("[IA-PROCESSOR] Message hash cache cleared");
		}
		return isDuplicate;
	}

	private void markAsProcessed(String messageHash) {
		processedMessageHashes.add(messageHash);
		log.debug("[IA-PROCESSOR] Message marked as processed: {}", messageHash);
	}

	/**
	 * UNIFIED NORMALIZATION
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> normalizeInput(Object input) {
		try {
			// byte[] → String
			if (input instanceof byte[] bytes) {
				input = new String(bytes, StandardCharsets.UTF_8);
				log.debug("[IA-PROCESSOR] Converted byte[] to String");
			}

			// Already Map
			if (input instanceof Map) {
				Map<String, Object> mapInput = (Map<String, Object>) input;
				log.debug("[IA-PROCESSOR] Input is Map with keys: {}", mapInput.keySet());
				return mapInput;
			}

			// String processing
			if (input instanceof String stringInput) {
				log.debug("[IA-PROCESSOR] Processing String, length: {}", stringInput.length());
				return normalizeStringInput(stringInput.trim());
			}

			// IaRequest conversion
			if (input instanceof IaRequest iaRequest) {
				log.debug("[IA-PROCESSOR] Converting IaRequest to Map");
				return mapper.convertValue(iaRequest, Map.class);
			}

			// Generic object conversion
			log.debug("[IA-PROCESSOR] Converting {} to Map", input.getClass().getSimpleName());
			return mapper.convertValue(input, Map.class);

		} catch (Exception e) {
			log.error("[IA-PROCESSOR] Normalization error: {}", e.getMessage());
			return null;
		}
	}

	private Map<String, Object> normalizeStringInput(String input) {
		// 1. Direct JSON
		if (isJsonLike(input)) {
			log.debug("[IA-PROCESSOR] Attempting JSON parsing");
			Map<String, Object> parsed = tryParseJson(input);
			if (parsed != null) {
				log.debug("[IA-PROCESSOR] JSON parsed successfully");
				return parsed;
			}
		}

		// 2. Base64 decoding
		if (messageWrapper.isBase64(input)) {
			log.debug("[IA-PROCESSOR] Attempting Base64 decoding");
			Map<String, Object> unwrapped = messageWrapper.unwrapFromBase64(input);
			if (unwrapped != null) {
				log.info("[IA-PROCESSOR] Base64 decoded successfully");
				return unwrapped;
			}
		}

		// 3. Quoted JSON
		if ((input.startsWith("\"") && input.endsWith("\"")) || (input.startsWith("'") && input.endsWith("'"))) {

			log.debug("[IA-PROCESSOR] Removing quotes and retrying");
			String unquoted = input.substring(1, input.length() - 1).replace("\\\"", "\"").replace("\\n", "\n")
					.replace("\\t", "\t");

			return normalizeStringInput(unquoted);
		}

		log.warn("[IA-PROCESSOR] Could not normalize string input");
		return null;
	}

	/**
	 * ENHANCED MESSAGE TYPE IDENTIFICATION
	 */
	private MessageType identifyMessageType(Map<String, Object> input) {
		// 1. Check envelope type first (CRITICAL ANTI-LOOP)
		String envelopeType = (String) input.get("type");
		if (envelopeType != null) {
			log.debug("[IA-PROCESSOR] Found envelope type: {}", envelopeType);

			// IMMEDIATE LOOP PREVENTION
			if (IGNORED_ENVELOPE_TYPES.contains(envelopeType)) {
				log.warn("[IA-PROCESSOR] DETECTED {} - PREVENTING LOOP", envelopeType);
				return MessageType.IA_RESPONSE_LOOP;
			}

			// Extract payload for deeper analysis
			Object payload = input.get("payload");
			if (payload instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> payloadMap = (Map<String, Object>) payload;
				return identifyMessageTypeFromPayload(payloadMap, envelopeType);
			}

			// Use envelope type if no payload
			return mapEnvelopeTypeToMessageType(envelopeType);
		}

		// 2. No envelope - analyze directly
		return identifyMessageTypeFromPayload(input, null);
	}

	private MessageType identifyMessageTypeFromPayload(Map<String, Object> payload, String envelopeType) {
		log.debug("[IA-PROCESSOR] Analyzing payload keys: {}", payload.keySet());

		// 1. CRITICAL: Response structure detection (ANTI-LOOP)
		if (hasResponseSignature(payload)) {
			log.warn("[IA-PROCESSOR] RESPONSE SIGNATURE DETECTED - LOOP PREVENTION");
			return MessageType.IA_RESPONSE_LOOP;
		}

		// 2. Startup tests
		String source = (String) payload.getOrDefault("source", "");
		if (source.contains("startup") || "workspace-startup".equals(source)
				|| KafkaDebug.WORKSPACE_STARTUP.equals(source)) {
			log.info("[IA-PROCESSOR] Identified as STARTUP_TEST");
			return MessageType.STARTUP_TEST;
		}

		// 3. Connection tests
		String chatId = (String) payload.getOrDefault("chatId", "");
		if (chatId.startsWith("test-") || payload.containsKey("test") || source.contains("connection-test")) {
			log.info("[IA-PROCESSOR] Identified as CONNECTION_TEST");
			return MessageType.CONNECTION_TEST;
		}

		// 4. Valid IA request
		if (hasValidIaRequestStructure(payload)) {
			log.info("[IA-PROCESSOR] IDENTIFIED AS VALID IA_REQUEST");
			return MessageType.IA_REQUEST;
		}

		// 5. Unknown
		log.warn("[IA-PROCESSOR] Message type UNKNOWN");
		return MessageType.UNKNOWN;
	}

	private MessageType mapEnvelopeTypeToMessageType(String envelopeType) {
		return switch (envelopeType) {
		case "IA_REQUEST" -> MessageType.IA_REQUEST;
		case "STARTUP_TEST" -> MessageType.STARTUP_TEST;
		case "CONNECTION_TEST" -> MessageType.CONNECTION_TEST;
		default -> MessageType.UNKNOWN;
		};
	}

	/**
	 * HELPER METHODS
	 */
	private boolean hasResponseSignature(Map<String, Object> input) {
		long responseFieldCount = input.keySet().stream().filter(RESPONSE_SIGNATURE_FIELDS::contains).count();

		boolean hasSignature = responseFieldCount >= 2;
		if (hasSignature) {
			log.debug("[IA-PROCESSOR] Response signature detected: {} fields matching", responseFieldCount);
		}
		return hasSignature;
	}

	private boolean hasValidIaRequestStructure(Map<String, Object> input) {
		String chatId = (String) input.getOrDefault("chatId", "");
		String prompt = (String) input.getOrDefault("prompt", "");

		boolean isValid = !chatId.isBlank() && !prompt.isBlank() && !chatId.startsWith("test-")
				&& input.containsKey("options");

		if (isValid) {
			log.debug("[IA-PROCESSOR] Valid IA request - ChatId: {}, Prompt length: {}", chatId, prompt.length());
		} else {
			log.debug("[IA-PROCESSOR] Invalid IA request - ChatId: '{}', Prompt length: {}, HasOptions: {}", chatId,
					prompt.length(), input.containsKey("options"));
		}
		return isValid;
	}

	private boolean isJsonLike(String s) {
		return (s.startsWith("{") && s.endsWith("}")) || (s.startsWith("[") && s.endsWith("]"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> tryParseJson(String s) {
		try {
			return mapper.readValue(s, Map.class);
		} catch (Exception e) {
			log.debug("[IA-PROCESSOR] JSON parsing failed: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * MAIN IA PROCESSING METHOD
	 */
	private IaResponse processIaRequest(IaRequest request) {
		String chatId = request.getChatId();

		if (chatId == null) {
			throw new IllegalArgumentException("chatId absent in request.");
		}

		// Ignore test chatIds
		if (chatId.startsWith(TEST_CHAT_ID_PREFIX)) {
			log.info("[IA-PROCESSOR] IGNORING TEST CHATID: {}", chatId);
			return null;
		}

		try {
			log.info("[IA-PROCESSOR] PROCESSING REAL IA REQUEST: {}", chatId);

			Map<String, Object> opts = request.getOptions() != null ? request.getOptions() : Map.of();

			String apiKey = (String) opts.getOrDefault("api_key", null);
			if (apiKey == null) {
				throw new IllegalArgumentException("api_key absent in options.");
			}

			Double temperature = normalizeTemperatureFromOpts(opts);
			Integer maxOutputTokens = opts.containsKey("max_output_tokens")
					? Integer.valueOf(String.valueOf(opts.get("max_output_tokens")))
					: null;
			String instructions = (String) opts.getOrDefault("instructions", null);
			String model = (String) opts.getOrDefault("model", "gpt-4");

			log.debug("[IA-PROCESSOR] Request params - Model: {}, Temperature: {}, MaxTokens: {}", model, temperature,
					maxOutputTokens);

			List<ResponsesRequest.ContentBlock> contextBlocks = buildBlocksFromContextShards(opts);
			contextBlocks
					.add(ResponsesRequest.ContentBlock.builder().type("input_text").text(request.getPrompt()).build());

			var input = List.of(ResponsesRequest.InputItem.builder().role("user").content(contextBlocks).build());

			var builder = ResponsesRequest.builder().model(model).instructions(instructions).input(input)
					.temperature(temperature).maxOutputTokens(maxOutputTokens).metadata(Map.of(CHAT_ID, chatId))
					.promptCacheKey(chatId).safetyIdentifier(chatId);

			var responsesReq = builder.build();

			log.info("[IA-PROCESSOR] CALLING EXTERNAL AI API");
			ResponsesResponse res = responsesClient.createResponse(apiKey, responsesReq);

			String resposta = null;
			if (res.getOutput() != null && !res.getOutput().isEmpty() && res.getOutput().get(0).getContent() != null
					&& !res.getOutput().get(0).getContent().isEmpty()) {
				var first = res.getOutput().get(0).getContent().get(0);
				if (first != null) {
					resposta = first.getText();
				}
			}
			if (resposta == null) {
				resposta = "(no textual output)";
			}

			int tokensPrompt = getInt(res.getUsage(), "input_tokens");
			int tokensResposta = getInt(res.getUsage(), "output_tokens");
			BigDecimal custo = OpenAICustoUtil.calcularCustoPorUsage(res.getModel(), tokensPrompt, tokensResposta);

			IaResponse iaResponse = IaResponse.success(chatId, resposta, custo, res.getModel(), tokensPrompt,
					tokensResposta);

			log.info("[IA-PROCESSOR] SUCCESS - RESPONSE READY FOR: {}", chatId);
			log.debug("[IA-PROCESSOR] Response stats - Length: {}, Cost: {}, Tokens: {}/{}", resposta.length(), custo,
					tokensPrompt, tokensResposta);

			return iaResponse;

		} catch (Exception e) {
			log.error("[IA-PROCESSOR] ERROR PROCESSING {}: {}", chatId, e.getMessage(), e);

			if (isTransient(e)) {
				throw new IllegalStateException("Transient failure when calling/processing IA", e);
			}

			return IaResponse.error(chatId, "Error processing IA: " + e.getMessage());
		}
	}

	// EXISTING HELPER METHODS - UNCHANGED
	private List<ResponsesRequest.ContentBlock> buildBlocksFromContextShards(Map<String, Object> opts) {
		Object rawShards = opts.get("context_shards");
		if (!(rawShards instanceof List<?> l) || l.isEmpty()) {
			return new ArrayList<>();
		}

		List<Map<String, Object>> shardList = new ArrayList<>();
		for (Object o : l) {
			if (o instanceof Map<?, ?> m) {
				@SuppressWarnings("unchecked")
				Map<String, Object> mm = (Map<String, Object>) m;
				shardList.add(mm);
			}
		}

		shardList.sort(Comparator.<Map<String, Object>, Boolean>comparing(s -> !Boolean.TRUE.equals(s.get("stable")))
				.thenComparing(s -> String.valueOf(s.getOrDefault("type", "")))
				.thenComparingInt(s -> parseIntSafe(s.get("version"))));

		List<ResponsesRequest.ContentBlock> blocks = new ArrayList<>();
		for (Map<String, Object> s : shardList) {
			String type = String.valueOf(s.getOrDefault("type", ""));
			int version = parseIntSafe(s.get("version"));
			boolean stable = Boolean.TRUE.equals(s.get("stable"));
			Object payload = s.get("payload");

			String jsonPayload;
			try {
				jsonPayload = mapper.writeValueAsString(payload == null ? Map.of() : payload);
			} catch (Exception e) {
				jsonPayload = "{\"error\":\"failed to serialize shard %s\"}".formatted(type);
			}

			String header = "### CTX:%s v%d%s".formatted(type, version, stable ? " (stable)" : "");
			blocks.add(ResponsesRequest.ContentBlock.builder().type("input_text").text(header + "\n" + jsonPayload)
					.build());
		}

		return blocks;
	}

	private boolean isTransient(Throwable t) {
		Throwable e = t;
		while (e != null) {
			if (e instanceof java.io.IOException || e instanceof java.net.ConnectException
					|| e instanceof java.net.SocketTimeoutException
					|| e instanceof java.util.concurrent.TimeoutException) {
				return true;
			}

			if (e instanceof HttpStatusCodeException httpEx) {
				int code = httpEx.getStatusCode().value();
				if (code == 429 || (code >= 500 && code < 600)) {
					return true;
				}
			}

			String msg = e.getMessage();
			if (msg != null) {
				String m = msg.toLowerCase();
				if (m.contains("timeout") || m.contains("timed out") || m.contains("temporarily unavailable")
						|| m.contains("rate limit") || m.contains("429") || m.matches(".*\\b5\\d\\d\\b.*")) {
					return true;
				}
			}
			e = e.getCause();
		}
		return false;
	}

	private int getInt(Map<String, Object> usage, String key) {
		if (usage == null || !usage.containsKey(key) || usage.get(key) == null) {
			return 0;
		}
		return Integer.parseInt(String.valueOf(usage.get(key)));
	}

	private static int parseIntSafe(Object v) {
		if (v == null) {
			return 0;
		}
		try {
			return Integer.parseInt(String.valueOf(v));
		} catch (Exception e) {
			return 0;
		}
	}

	private static double normalizeTemperatureFromOpts(Map<String, Object> opts) {
		final double def = DEFAULT_TEMP;
		if (opts == null) {
			return def;
		}

		Object t = opts.get("temperature");
		if (t == null) {
			return def;
		}

		double v;
		if (t instanceof Number n) {
			v = n.doubleValue();
		} else {
			try {
				v = Double.parseDouble(String.valueOf(t).trim());
			} catch (Exception e) {
				return def;
			}
		}

		if (!Double.isFinite(v)) {
			return def;
		}

		v = Math.max(0.0, Math.min(100.0, v));
		double scaled = (v / 100.0) * 2.0;
		return Math.round(scaled * 1000.0) / 1000.0;
	}
}