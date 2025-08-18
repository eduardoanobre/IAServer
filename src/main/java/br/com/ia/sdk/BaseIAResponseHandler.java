package br.com.ia.sdk;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * BASE IA RESPONSE HANDLER - SDK Shared Component
 * 
 * This is a shared base class for all modules that need to receive responses
 * from IAServer. Place this in the SDK package (br.com.ia.sdk) to be shared
 * across all modules.
 * 
 * Usage: 1. Workspace: WorkspaceIaResponseHandler extends BaseIAResponseHandler
 * 2. Marketing: MarketingIaResponseHandler extends BaseIAResponseHandler 3.
 * ERP: ErpIaResponseHandler extends BaseIAResponseHandler
 * 
 * Each module implements its own @Bean iaReplies() function that calls
 * processResponse()
 */
@Slf4j
public abstract class BaseIAResponseHandler {

	protected final ObjectMapper objectMapper;

	@Value("${ia.base64.wrapper.enabled:true}")
	protected boolean base64WrapperEnabled;

	@Value("${spring.application.name:unknown-module}")
	protected String moduleName;

	protected BaseIAResponseHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		log.info("[BASE-IA-HANDLER] Initialized for module: {}, Base64 enabled: {}", moduleName, base64WrapperEnabled);
	}

	/**
	 * Main processing method - called by module-specific @Bean iaReplies()
	 * functions
	 * 
	 * Example usage in module:
	 * 
	 * @Bean public Function<String, Void> iaReplies() { return response -> {
	 *       processResponse(response); return null; }; }
	 */
	public void processResponse(String response) {
		try {
			if (log.isDebugEnabled()) {
				log.debug("[PROCESS-RESPONSE] Processing in {} - Length: {} chars", moduleName, response.length());
				log.debug("[PROCESS-RESPONSE] Preview: {}",
						response.length() > 100 ? response.substring(0, 100) + "..." : response);
			}

			// Unwrap Base64 message
			Map<String, Object> unwrapped = unwrapFromBase64(response);
			if (unwrapped == null) {
				log.warn("[PROCESS-RESPONSE] Failed to unwrap Base64, trying plain JSON");
				unwrapped = objectMapper.readValue(response, Map.class);
			}

			// Process the response
			routeResponse(unwrapped);

		} catch (Exception e) {
			log.error("[PROCESS-RESPONSE] Error processing response in {}: {}", moduleName, e.getMessage(), e);
			onProcessingException(response, e);
		}
	}

	/**
	 * Routes the response to appropriate handler based on type
	 */
	protected void routeResponse(Map<String, Object> response) {
		String responseType = (String) response.getOrDefault("type", "UNKNOWN");
		String sourceModule = (String) response.getOrDefault("module", "unknown");
		Long timestamp = (Long) response.getOrDefault("timestamp", 0L);

		log.debug("[ROUTE-RESPONSE] Type: {}, Source: {}, Target: {}, Timestamp: {}", responseType, sourceModule,
				moduleName, timestamp);

		switch (responseType) {
		case "IA_RESPONSE" -> handleIaResponse(response);
		case "PROCESSING_RESPONSE" -> handleProcessingResponse(response);
		case "ERROR_RESPONSE" -> handleErrorResponse(response);
		case "STARTUP_TEST" -> handleStartupTest(response);
		case "CONNECTION_TEST" -> handleConnectionTest(response);
		default -> {
			log.warn("[ROUTE-RESPONSE] Unknown type: {} from {} to {}", responseType, sourceModule, moduleName);
			handleUnknownResponse(response);
		}
		}
	}

	/**
	 * Handles IA_RESPONSE - Main AI processing results
	 */
	protected void handleIaResponse(Map<String, Object> response) {
		try {
			Object payload = response.get("payload");
			if (!(payload instanceof Map)) {
				log.error("[IA-RESPONSE] Invalid payload structure in response");
				return;
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> iaResponseData = (Map<String, Object>) payload;

			String chatId = (String) iaResponseData.get("chatId");
			String resposta = (String) iaResponseData.get("resposta");
			Boolean success = (Boolean) iaResponseData.getOrDefault("success", false);

			// Extract additional fields from IAServer response
			Object custo = iaResponseData.get("custo");
			String modelo = (String) iaResponseData.get("modelo");
			Object tokensPrompt = iaResponseData.get("tokensPrompt");
			Object tokensResposta = iaResponseData.get("tokensResposta");

			if (Boolean.TRUE.equals(success)) {
				log.info("[IA-RESPONSE] SUCCESS - ChatId: {}, Model: {}, Response length: {}", chatId, modelo,
						resposta != null ? resposta.length() : 0);

				if (log.isDebugEnabled()) {
					log.debug("[IA-RESPONSE] Cost: {}, Prompt tokens: {}, Response tokens: {}", custo, tokensPrompt,
							tokensResposta);
				}

				onSuccessfulIaResponse(chatId, resposta, iaResponseData);

			} else {
				String errorMessage = (String) iaResponseData.get("errorMessage");
				log.error("[IA-RESPONSE] ERROR - ChatId: {}, Error: {}", chatId, errorMessage);

				onIaError(chatId, errorMessage, iaResponseData);
			}

		} catch (Exception e) {
			log.error("[IA-RESPONSE] Exception handling response in {}: {}", moduleName, e.getMessage(), e);
		}
	}

	/**
	 * Handles PROCESSING_RESPONSE - IAServer internal status messages
	 */
	protected void handleProcessingResponse(Map<String, Object> response) {
		String status = (String) response.getOrDefault("status", "unknown");
		String reason = (String) response.getOrDefault("reason", "unknown");
		String message = (String) response.getOrDefault("message", "");

		log.info("[PROCESSING-RESPONSE] Status: {}, Reason: {}, Module: {}", status, reason, moduleName);

		switch (status) {
		case "ignored" -> {
			log.debug("[PROCESSING-RESPONSE] Message ignored - Reason: {}", reason);
			if ("startup_test".equals(reason) || "connection_test".equals(reason)) {
				log.info("[PROCESSING-RESPONSE] Test message processed successfully");
			}
			onProcessingIgnored(reason, message, response);
		}
		case "error" -> {
			log.warn("[PROCESSING-RESPONSE] Processing error - Reason: {}, Message: {}", reason, message);
			onProcessingError(reason, message, response);
		}
		default -> {
			log.info("[PROCESSING-RESPONSE] Status: {}, Message: {}", status, message);
			onProcessingStatus(status, reason, message, response);
		}
		}
	}

	/**
	 * Handles ERROR_RESPONSE - IAServer error notifications
	 */
	protected void handleErrorResponse(Map<String, Object> response) {
		String message = (String) response.getOrDefault("message", "Unknown error");
		String reason = (String) response.getOrDefault("reason", "unknown");

		log.error("[ERROR-RESPONSE] IAServer error to {}: {} (Reason: {})", moduleName, message, reason);

		onGeneralError(message, response);
	}

	/**
	 * Handles test responses
	 */
	protected void handleStartupTest(Map<String, Object> response) {
		log.info("[STARTUP-TEST] Startup test response received in {}", moduleName);
		onStartupTest(response);
	}

	protected void handleConnectionTest(Map<String, Object> response) {
		log.info("[CONNECTION-TEST] Connection test response received in {}", moduleName);
		onConnectionTest(response);
	}

	/**
	 * Handles unknown response types
	 */
	protected void handleUnknownResponse(Map<String, Object> response) {
		log.warn("[UNKNOWN-RESPONSE] Unrecognized format in {}: {}", moduleName, response.keySet());

		if (log.isDebugEnabled()) {
			log.debug("[UNKNOWN-RESPONSE] Full response: {}", response);
		}

		onUnknownResponse(response);
	}

	/**
	 * Unwraps Base64 encoded message from IAServer
	 */
	protected Map<String, Object> unwrapFromBase64(String base64Message) {
		try {
			if (!base64WrapperEnabled) {
				log.debug("[UNWRAP-BASE64] Base64 wrapper disabled, using plain JSON");
				return null;
			}

			// Decode Base64
			byte[] decoded = Base64.getDecoder().decode(base64Message);
			String json = new String(decoded, StandardCharsets.UTF_8);

			// Parse JSON envelope
			@SuppressWarnings("unchecked")
			Map<String, Object> envelope = objectMapper.readValue(json, Map.class);

			String type = (String) envelope.get("type");
			String sourceModule = (String) envelope.get("module");

			log.debug("[UNWRAP-BASE64] Decoded - Type: {}, Source: {}, Target: {}", type, sourceModule, moduleName);

			return envelope;

		} catch (IllegalArgumentException e) {
			log.debug("[UNWRAP-BASE64] Not valid Base64 in {}: {}", moduleName, e.getMessage());
			return null;
		} catch (Exception e) {
			log.warn("[UNWRAP-BASE64] Failed to unwrap Base64 in {}: {}", moduleName, e.getMessage());
			return null;
		}
	}

	// =============================================================================
	// ABSTRACT/OVERRIDE METHODS - IMPLEMENT IN MODULE-SPECIFIC HANDLERS
	// =============================================================================

	/**
	 * Called when IA processing succeeds - IMPLEMENT IN MODULE HANDLER
	 */
	protected abstract void onSuccessfulIaResponse(String chatId, String resposta, Map<String, Object> fullResponse);

	/**
	 * Called when IA processing fails - IMPLEMENT IN MODULE HANDLER
	 */
	protected abstract void onIaError(String chatId, String errorMessage, Map<String, Object> fullResponse);

	/**
	 * Called for processing status ignored - OVERRIDE IF NEEDED
	 */
	protected void onProcessingIgnored(String reason, String message, Map<String, Object> fullResponse) {
		log.debug("[IGNORED-HANDLER] Default implementation - Reason: {}", reason);
		// Override in module handler if needed
	}

	/**
	 * Called for processing errors - OVERRIDE IF NEEDED
	 */
	protected void onProcessingError(String reason, String message, Map<String, Object> fullResponse) {
		log.warn("[PROCESSING-ERROR] Default implementation - Reason: {}, Message: {}", reason, message);
		// Override in module handler if needed
	}

	/**
	 * Called for other processing statuses - OVERRIDE IF NEEDED
	 */
	protected void onProcessingStatus(String status, String reason, String message, Map<String, Object> fullResponse) {
		log.info("[STATUS-HANDLER] Default implementation - Status: {}", status);
		// Override in module handler if needed
	}

	/**
	 * Called for general errors - OVERRIDE IF NEEDED
	 */
	protected void onGeneralError(String errorMessage, Map<String, Object> fullResponse) {
		log.error("[GENERAL-ERROR] Default implementation - Error: {}", errorMessage);
		// Override in module handler if needed
	}

	/**
	 * Called for startup tests - OVERRIDE IF NEEDED
	 */
	protected void onStartupTest(Map<String, Object> response) {
		log.info("[STARTUP-TEST] Default implementation");
		// Override in module handler if needed
	}

	/**
	 * Called for connection tests - OVERRIDE IF NEEDED
	 */
	protected void onConnectionTest(Map<String, Object> response) {
		log.info("[CONNECTION-TEST] Default implementation");
		// Override in module handler if needed
	}

	/**
	 * Called for unknown responses - OVERRIDE IF NEEDED
	 */
	protected void onUnknownResponse(Map<String, Object> response) {
		log.warn("[UNKNOWN-RESPONSE] Default implementation");
		// Override in module handler if needed
	}

	/**
	 * Called when response processing fails - OVERRIDE IF NEEDED
	 */
	protected void onProcessingException(String originalResponse, Exception e) {
		log.error("[PROCESSING-EXCEPTION] Default implementation - Error: {}", e.getMessage());
		// Override in module handler if needed
	}
}