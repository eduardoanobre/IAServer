package br.com.ia.sdk.transport;

import br.com.ia.sdk.context.dto.ContextShardDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromptRequestPayload(
    String correlationId,
    String chatId,
    String model,
    String prompt,
    List<ContextShardDTO> shards,
    Integer maxTokens,
    Double temperature
) {}
