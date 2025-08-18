package br.com.ia.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IaRequest {

	// Constantes para os campos do options Map
	public static final String TYPE = "type";
	public static final String API_KEY = "api_key";
	public static final String MODEL = "model";
	public static final String INSTRUCTIONS = "instructions";
	public static final String TEMPERATURE = "temperature";
	public static final String MAX_OUTPUT_TOKENS = "max_output_tokens";
	public static final String CONTEXT_SHARDS = "context_shards";
	public static final String TEXT = "text";
	public static final String MODULE_KEY = "module_key";
	public static final String VERSAO_SCHEMA = "versao_schema";
	public static final String VERSAO_REGRAS_MODULO = "versao_regras_modulo";

	// Constantes para os campos do ContextShard
	public static final String SHARD_TYPE = "type";
	public static final String SHARD_VERSION = "version";
	public static final String SHARD_STABLE = "stable";
	public static final String SHARD_PAYLOAD = "payload";
	
	public static final String REQUEST_TYPE_COMMAND = "command";
	public static final String REQUEST_TYPE_TEST = "test";

	private String chatId;
	private String prompt;
	private Map<String, Object> options;

	// getters and setters

	/**
	 * Gets the API key from options
	 *
	 * @return API key or null if not present
	 */
	public String getApiKey() {
		return options != null ? (String) options.get(API_KEY) : null;
	}

	/**
	 * Gets the model from options with default fallback
	 *
	 * @return Model name or "gpt-5" as default
	 */
	public String getModel() {
		return options != null ? (String) options.getOrDefault(MODEL, "gpt-5") : "gpt-5";
	}

	/**
	 * Gets the temperature from options with default fallback
	 *
	 * @return Temperature value or 0.3 as default
	 */
	public Double getTemperature() {
		if (options == null)
			return 0.3;
		Object temp = options.get(TEMPERATURE);
		if (temp instanceof Number numero) {
			return numero.doubleValue();
		}
		return 0.3;
	}

	/**
	 * Checks if this is a valid request (has required fields)
	 *
	 * @return true if request has chatId, prompt, and api_key
	 */
	public boolean isValid() {
		return chatId != null && !chatId.trim().isEmpty() && prompt != null && !prompt.trim().isEmpty()
				&& getApiKey() != null && !getApiKey().trim().isEmpty();
	}
}