package br.com.ia.sdk.context.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL) // remove nulos do JSON
public record ContextShardDTO(
    String type,                 // ex.: "PROJETO", "SPRINT", "USUARIO"
    String id,                   // id/chave (quando houver)
    Integer version,             // versão do shard
    Boolean stable,              // se é estável ou efêmero
    Map<String, Object> payload  // conteúdo específico do shard
) {}
