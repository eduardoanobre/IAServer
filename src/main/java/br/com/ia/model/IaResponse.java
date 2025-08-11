package br.com.ia.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.shared.annotations.Comment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload com o resultado do processamento de IA via Kafka (Responses API).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IaResponse {

    @Comment("ID único do chat (UUID). Mesmo valor enviado na requisição.")
    private String chatId;

    @Comment("Conteúdo retornado pela IA (JSON conforme schema instruído).")
    private String resposta;

    private BigDecimal custo;
    private String modelo;

    // usage.input_tokens e usage.output_tokens
    private int tokensPrompt;
    private int tokensResposta;
}
