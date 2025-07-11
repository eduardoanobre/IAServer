package br.com.ia.model;

import java.util.List;

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
public class ChatCompletionRequest {

	private String model;
	private List<ChatMessage> messages;
	private Double temperature;

	@JsonProperty("max_tokens")
	private Integer maxTokens;

	// Outros campos podem ser adicionados conforme necessidade (top_p, n, stop,
	// etc.)

}