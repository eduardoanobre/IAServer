package br.com.ia.processor.core;

import java.util.Map;

import org.springframework.stereotype.Component;

import br.com.ia.sdk.Base64MessageWrapper;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class IaResponseFactory {

	private final Base64MessageWrapper wrapper;

	public String standard(String status, String reason, String message, Object originalData) {
		 Map<String,Object> body = Map.of(
		            "status", status,
		            "reason", reason,
		            "message", message,
		            "timestamp", System.currentTimeMillis(),
		            "processor", "IaProcessor",
		            "originalData", originalData != null ? originalData : Map.of()
		        );
		return wrapper.wrapToBase64(body, "PROCESSING_RESPONSE");
	}

	public String ignored(String reason, String message) {
		return standard("ignored", reason, message, null);
	}

	public String error(String reason, String message) {
		return standard("error", reason, message, null);
	}
}
