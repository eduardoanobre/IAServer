package br.com.ia.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para requisição de chat completions via Kafka
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionRequest {

	/**
	 * Provedor de IA: "openai", "gemini" etc. Se não informado, usa padrão
	 * configurado.
	 */
	private String provider;

	/**
	 * Chave de API para override; se não informado, usa a configurada para o
	 * provider.
	 */
	@JsonProperty("api_key")
	private String apiKey;

	/**
	 * Modelo a ser utilizado; se não informado, usa padrão do provider.
	 */
	private String model;

	/**
	 * Mensagens no formato chat.
	 */
	private List<ChatMessage> messages;

	/**
	 * Temperatura para sampling (0.0 a 2.0).
	 */
	private Double temperature;

	/**
	 * Número máximo de tokens para geração.
	 */
	@JsonProperty("max_tokens")
	private Integer maxTokens;

	/**
	 * id de assitant
	 */
	private String assistantId;

}