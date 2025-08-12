package br.com.ia.model.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponsesRequest {

  // Modelo
  private String model; // ex.: "gpt-5"

  // Núcleo
  private List<InputItem> input;       // blocos: input_text, input_image, input_file
  private String instructions;         // system/developer message

  // Referência a template de prompt
  private PromptRef prompt;            // id + (opcional) version + variables

  // Amostragem / limites
  private Double temperature;
  @JsonProperty("top_p") private Double topP;
  @JsonProperty("top_logprobs") private Integer topLogprobs;
  @JsonProperty("max_output_tokens") private Integer maxOutputTokens;

  // Ferramentas
  private List<ToolDefinition> tools;      // built-ins + funções (function calling)
  @JsonProperty("tool_choice") private Object toolChoice; // "auto" | "none" | objeto
  @JsonProperty("max_tool_calls") private Integer maxToolCalls;
  @JsonProperty("parallel_tool_calls") private Boolean parallelToolCalls;

  // Estado / stream
  @JsonProperty("previous_response_id") private String previousResponseId;
  private Boolean stream;                  // SSE semântico
  @JsonProperty("stream_options") private StreamOptions streamOptions;
  private Boolean store;                   // default: true
  private Boolean background;              // run em background
  private List<String> include;            // ex.: message.output_text.logprobs, file_search_call.results, etc.

  // Controle / metadata
  private Map<String,String> metadata;
  @JsonProperty("prompt_cache_key") private String promptCacheKey;
  @JsonProperty("safety_identifier") private String safetyIdentifier;
  @JsonProperty("service_tier") private ServiceTier serviceTier;
  private Truncation truncation;           // auto | disabled
  private Verbosity verbosity;             // low | medium | high

  // Opções para modelos de raciocínio (o-series)
  private ReasoningOptions reasoning;      // ex.: effort = low|medium|high

  // Saídas estruturadas (JSON garantido)
  private TextOptions text;

  /* ===== Tipos auxiliares ===== */

  @Data @Builder @NoArgsConstructor @AllArgsConstructor
  public static class InputItem {
    private String role; // "user" | "assistant" | "system"
    private List<ContentBlock> content;
  }

  @Data @Builder @NoArgsConstructor @AllArgsConstructor
  public static class ContentBlock {
    private String type;                        // "input_text" | "input_image" | "input_file"
    private String text;                        // quando input_text
    @JsonProperty("image_url") private String imageUrl; // quando input_image
    @JsonProperty("file_id") private String fileId;     // quando input_file
  }

  @Data @Builder @NoArgsConstructor @AllArgsConstructor
  public static class ToolDefinition {
    private String type;                   // "function" | built-in: "web_search","file_search","code_interpreter","computer"
    private String name;                   // para function
    private String description;            // para function
    private Map<String,Object> parameters; // JSON Schema
  }

  @Data @Builder @NoArgsConstructor @AllArgsConstructor
  public static class StreamOptions {
    @JsonProperty("include_usage") private Boolean includeUsage;
  }

  @Data @Builder @NoArgsConstructor @AllArgsConstructor
  public static class TextOptions {
    @JsonProperty("response_format") private ResponseFormat responseFormat;
  }

  @Data @Builder @NoArgsConstructor @AllArgsConstructor
  public static class ResponseFormat {
    private String type; // "text" | "json_schema"
    @JsonProperty("json_schema") private JsonSchema jsonSchema;
    @JsonProperty("strict") private Boolean strict; 
  }

  @Data @Builder @NoArgsConstructor @AllArgsConstructor
  public static class JsonSchema {
    private String name;
    private Map<String,Object> schema; // JSON Schema padrão
  }

  @Data @Builder @NoArgsConstructor @AllArgsConstructor
  public static class PromptRef {
    private String id;                      // ID do template no dashboard
    private String version;                 // opcional
    private Map<String,Object> variables;   // variáveis do template
  }

  @Data @Builder @NoArgsConstructor @AllArgsConstructor
  public static class ReasoningOptions {
    private String effort; // "low" | "medium" | "high" (o-series)
  }

  public enum Truncation { auto, disabled }
  public enum Verbosity { low, medium, high }

  public enum ServiceTier {
    @JsonProperty("auto") auto,
    @JsonProperty("default") _default,   // serializa como "default"
    @JsonProperty("flex") flex,
    @JsonProperty("priority") priority
  }
}
