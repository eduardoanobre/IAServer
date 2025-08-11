package br.com.ia.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload para requisição de processamento de IA via Kafka (Responses API).
 * - Identificador único padronizado: chatId (UUID gerado no ERP).
 * - Sem EnumModeloIA: o modelo agora vem em options["model"] (default "gpt-5").
 * - Demais parâmetros (temperature, max_output_tokens, instructions, etc.) vêm em options.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IaRequest {

    /** ID único do chat/conversa (UUID gerado no ERP e mantido em todo o fluxo) */
    private String chatId;

    /** Prompt de entrada para a chamada de IA */
    private String prompt;

    /**
     * Opções adicionais para a Responses API, por exemplo:
     * - api_key (String)
     * - model (String) ex.: "gpt-5"
     * - instructions (String)
     * - temperature (Number)
     * - max_output_tokens (Integer)
     * - prompt_cache_key (String), safety_identifier (String)
     * - metadata (Map<String,String>) — se preferir enviar daqui
     * - text.response_format / tools / tool_choice (quando aplicável)
     */
    private Map<String, Object> options;
}
