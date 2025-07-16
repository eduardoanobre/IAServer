package br.com.ia.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import br.com.shared.model.enums.EnumModeloIA;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionRequest {

	/** Modelo de IA a ser utilizado, com IA, nome e custos */
	private EnumModeloIA modeloIA;

	/** Chave da API, opcional (override) */
	@JsonProperty("api_key")
	private String apiKey;

	/** Conversa no formato chat */
	private List<ChatMessage> messages;

	/** Temperatura da resposta */
	private Double temperature;

	/** Limite de tokens */
	@JsonProperty("max_tokens")
	private Integer maxTokens;

	/** Usado apenas para Assistants */
	private String assistantId;
}
