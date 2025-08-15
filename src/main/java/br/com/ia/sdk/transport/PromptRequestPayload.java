package br.com.ia.sdk.transport;

import br.com.ia.sdk.context.dto.ContextShardDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// Copie somente os campos que realmente vão no fio (não exponha tudo).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromptRequestPayload(
    String chatId,
    String model,
    String prompt,
    List<ContextShardDTO> contextShards,
    Integer maxTokens,
    Double temperature
    // ... adicione os demais campos que você JÁ envia
) {}
