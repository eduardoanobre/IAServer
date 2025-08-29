package br.com.ia.processor;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ia.sdk.Base64MessageWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IaMessageNormalizer {

	private final ObjectMapper mapper;
	private final Base64MessageWrapper messageWrapper;

	@SuppressWarnings("unchecked")
	public Map<String, Object> normalize(Object input) {
		try {
			Object in = input;

			if (input instanceof ConsumerRecord<?, ?> value) {
				in = value.value();
			}

			if (in instanceof byte[] bytes) {
				in = new String(bytes, StandardCharsets.UTF_8);
				log.debug("[NORMALIZER] Converted byte[] to String");
			}

			if (in instanceof Map<?, ?> map) {
				log.debug("[NORMALIZER] Input is Map with keys: {}", map.keySet());
				return (Map<String, Object>) map;
			}

			if (in instanceof String s) {
				return normalizeString(s.trim());
			}

			log.debug("[NORMALIZER] Converting {} to Map", in.getClass().getSimpleName());
			return mapper.convertValue(in, Map.class);
		} catch (Exception e) {
			log.error("[NORMALIZER] Normalization error: {}", e.getMessage());
			return null; // NOSONAR
		}
	}

	private Map<String, Object> normalizeString(String s) {
		if (isJsonLike(s)) {
			Map<String, Object> parsed = tryParseJson(s);
			if (parsed != null) {
				log.debug("[NORMALIZER] JSON parsed successfully");
				return parsed;
			}
		}

		if (messageWrapper.isBase64(s)) {
			Map<String, Object> unwrapped = messageWrapper.unwrapFromBase64(s);
			if (unwrapped != null) {
				log.info("[NORMALIZER] Base64 decoded successfully");
				return unwrapped;
			}
		}

		if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
			String unquoted = s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\n", "\n").replace("\\t",
					"\t");
			return normalizeString(unquoted);
		}

		log.warn("[NORMALIZER] Could not normalize string input");
		return null; // NOSONAR
	}

	private boolean isJsonLike(String s) {
		return (s.startsWith("{") && s.endsWith("}")) || (s.startsWith("[") && s.endsWith("]"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> tryParseJson(String s) {
		try {
			return mapper.readValue(s, Map.class);
		} catch (Exception e) {
			log.debug("[NORMALIZER] JSON parsing failed: {}", e.getMessage());
			return Collections.emptyMap();
		}
	}
}
