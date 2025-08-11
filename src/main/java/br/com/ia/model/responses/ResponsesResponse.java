package br.com.ia.model.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data @NoArgsConstructor @AllArgsConstructor
public class ResponsesResponse {
  private String id;
  private String model;
  @JsonProperty("service_tier") private String serviceTier;
  private List<OutputItem> output;     // texto, tool calls, etc.
  private Map<String,Object> usage;    // tokens, etc.

  @Data @NoArgsConstructor @AllArgsConstructor
  public static class OutputItem {
    private String type;               // "message", "tool_call", etc.
    private String role;               // se for message
    private List<OutputContent> content;
    @JsonProperty("tool") private ToolCall tool; // quando type=tool_call
  }

  @Data @NoArgsConstructor @AllArgsConstructor
  public static class OutputContent {
    private String type;               // "output_text"
    private String text;               // conteúdo textual
  }

  @Data @NoArgsConstructor @AllArgsConstructor
  public static class ToolCall {
    private String type;               // "function" | built-in
    private String name;               // function name
    private Map<String,Object> arguments;
  }
}
