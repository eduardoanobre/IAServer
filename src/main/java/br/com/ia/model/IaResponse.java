package br.com.ia.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.shared.annotations.Comment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload com o resultado do processamento de IA via Kafka.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IaResponse {

	@Comment("Mesmo correlationId da requisição")
	private String correlationId;

	@Comment("Conteúdo retornado pela IA")
	private String resposta;

	BigDecimal custo;
	String modelo;

	int tokensPrompt;
	int tokensResposta;
}
