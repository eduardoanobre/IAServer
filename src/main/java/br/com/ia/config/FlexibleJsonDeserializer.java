package br.com.ia.config;

import java.util.Base64;
import java.util.Map;

import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlexibleJsonDeserializer extends JsonDeserializer<Object> {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public Object deserialize(String topic, Headers headers, byte[] data) {
		return deserialize(topic, data);
	}

	@Override
	public Object deserialize(String topic, byte[] data) {
		if (data == null || data.length == 0) {
			log.warn("Empty data received from topic: {}", topic);
			return Map.of("error", "empty_data");
		}

		try {
			String content = new String(data);
			log.debug("Content received: {}", content.substring(0, Math.min(100, content.length())));

			// Attempt 1: Direct JSON
			try {
				return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
				});
			} catch (Exception e) {
				log.debug("Not direct JSON: {}", e.getMessage());
			}

			// Attempt 2: Base64 decoded
			try {
				if (isBase64(content)) {
					byte[] decoded = Base64.getDecoder().decode(content);
					String decodedContent = new String(decoded);
					log.debug("Decoded content: {}", decodedContent);
					return objectMapper.readValue(decodedContent, new TypeReference<Map<String, Object>>() {
					});
				}
			} catch (Exception e) {
				log.debug("Error decoding Base64: {}", e.getMessage());
			}

			// Attempt 3: Create generic object
			log.warn("Creating generic object for unrecognized content");
			return Map.of("originalContent", content, "topic", topic, "timestamp", System.currentTimeMillis(), "type",
					"unknown_format");

		} catch (Exception e) {
			log.error("Total deserialization error for topic {}: {}", topic, e.getMessage());
			return Map.of("error", "deserialization_failed", "topic", topic, "message", e.getMessage(), "timestamp",
					System.currentTimeMillis());
		}
	}

	private boolean isBase64(String content) {
		if (content == null || content.trim().isEmpty()) {
			return false;
		}

		try {
			// Remove espaços e quebras de linha
			String cleaned = content.replaceAll("\\s", "");

			// Verifica se tem tamanho válido para Base64
			if (cleaned.length() % 4 != 0) {
				return false;
			}

			// Verifica se contém apenas caracteres Base64 válidos
			if (!cleaned.matches("^[A-Za-z0-9+/]*={0,2}$")) {
				return false;
			}

			// Tenta decodificar para verificar
			Base64.getDecoder().decode(cleaned);
			return true;

		} catch (Exception e) {
			return false;
		}
	}
}