package br.com.ia.model.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponsesResponse {
    private String correlation_id;
    private String chat_id;
    private String model;
    @JsonProperty("service_tier") 
    private String serviceTier;
    private List<OutputItem> output;     // texto, tool calls, etc.
    private Map<String, Object> usage;    // tokens, etc.
    private Metadata metadata;           // campo para metadata

    // Getter personalizado para correlation_id que vem do metadata
    public String getCorrelation_id() {
        if (metadata != null && metadata.getCorrelation_id() != null) {
            return metadata.getCorrelation_id();
        }
        return this.correlation_id; // fallback para o campo direto se existir
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutputItem {
        private String type;               // "message", "tool_call", etc.
        private String role;               // se for message
        private List<OutputContent> content;
        @JsonProperty("tool") 
        private ToolCall tool; // quando type=tool_call
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutputContent {
        private String type;               // "output_text"
        private String text;               // conteúdo textual
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String type;               // "function" | built-in
        private String name;               // function name
        private Map<String, Object> arguments;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        @JsonProperty("correlation_id")
        private String correlation_id;
        
        // outros campos que possam existir no metadata
        @JsonProperty("request_id")
        private String request_id;
        
        @JsonProperty("timestamp")
        private String timestamp;
    }
}