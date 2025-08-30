package br.com.ia.model;

import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.ia.model.enums.ModeloIA;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IaRequest implements Serializable{

	private static final long serialVersionUID = 5200498518764653883L;
	private static final ModeloIA MODELO_IA_DEFAULT = ModeloIA.GPT_5;

	public static final String API_KEY = "api_key";
	public static final String MODELO = "modelo";
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

	private String correlationId;
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
	 * @return Modelo name or "gpt-5" as default
	 */
	public ModeloIA getModelo() {

		if (options != null) {
			String m = (String) options.getOrDefault(MODELO, MODELO_IA_DEFAULT.name());
			return ModeloIA.from(m);
		}
		return MODELO_IA_DEFAULT;
	}

}