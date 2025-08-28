package br.com.ia.model.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Payload para POST https://api.openai.com/v1/responses
 * Campos opcionais não usados não serão serializados (NON_NULL).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponsesRequest {

  /* ========= Núcleo ========= */

  /** Nome do modelo (ex.: "gpt-4.1-mini", "gpt-4.1", "gpt-5" se habilitado) */
  private String model;

  /**
   * Entrada no formato da Responses API:
   * [
   *   { "role": "user", "content": [ { "type":"input_text","text":"..." }, ... ] }
   * ]
   */
  private List<InputItem> input;

  /** Instruções de sistema/desenvolvedor (contexto de alto nível) */
  private String instructions;

  /** Referência opcional a template de prompt (id/version/variables) */
  private PromptRef prompt;

  /* ========= Amostragem / limites ========= */

  private Double temperature;

  @JsonProperty("top_p")
  private Double topP;

  /** Quantidade de logprobs no output (0–20) */
  @JsonProperty("top_logprobs")
  private Integer topLogprobs;

  /** Limite de tokens da saída (nome correto na Responses API) */
  @JsonProperty("max_output_tokens")
  private Integer maxOutputTokens;

  /* ========= Ferramentas ========= */

  /**
   * Definições de ferramentas:
   * - built-ins: "web_search", "file_search", "code_interpreter", "computer"
   * - function calling: type="function" + name/description/parameters (JSON Schema)
   */
  private List<ToolDefinition> tools;

  /**
   * "auto" | "none" | objeto (ex.: {"type":"function","name":"minhaFunc"})
   * Use Object para permitir os três formatos.
   */
  @JsonProperty("tool_choice")
  private Object toolChoice;

  /** Limite global de chamadas de ferramenta durante a geração */
  @JsonProperty("max_tool_calls")
  private Integer maxToolCalls;

  /** Permitir execuções paralelas de ferramentas */
  @JsonProperty("parallel_tool_calls")
  private Boolean parallelToolCalls;

  /* ========= Estado / stream ========= */

  /** Encadeia com uma resposta anterior sem reenviar histórico */
  @JsonProperty("previous_response_id")
  private String previousResponseId;

  /** Ativa Server-Sent Events semânticos */
  private Boolean stream;

  /** Opções do stream (ex.: incluir uso ao final) */
  @JsonProperty("stream_options")
  private StreamOptions streamOptions;

  /** Armazenar a interação (default: true no serviço) */
  private Boolean store;

  /**
   * Executar em background (quando suportado); se true, a resposta pode ser recuperada depois.
   * Se não usar esse fluxo, pode omitir.
   */
  private Boolean background;

  /**
   * Caminhos extras a incluir na resposta, ex.:
   * "message.output_text.logprobs", "input_image.image_url", "reasoning.encrypted_content"
   */
  private List<String> include;

  /* ========= Controle / metadata ========= */

  /** Metadados arbitrários (chave-valor) */
  private Map<String, String> metadata;

  /** Chave para cachear prompts em requisições subsequentes */
  @JsonProperty("prompt_cache_key")
  private String promptCacheKey;

  /** Identificador estável do usuário para políticas de segurança/moderação */
  @JsonProperty("safety_identifier")
  private String safetyIdentifier;

  /**
   * Camada de serviço. Suportado: "flex" | "priority".
   * Omitir para comportamento automático padrão.
   */
  @JsonProperty("service_tier")
  private ServiceTier serviceTier;

  /** Estratégia de truncamento de contexto: auto | disabled */
  private Truncation truncation;

  /** Nível de verbosidade (resumos mais sucintos vs detalhados) */
  private Verbosity verbosity;

  /** Opções para modelos de raciocínio (o-series / gpt-5) */
  private ReasoningOptions reasoning;

  /**
   * Saídas estruturadas/JSON garantido via text.format.
   * Exemplo de uso:
   * text: {
   *   format: {
   *     type: "json_schema",
   *     json_schema: { name: "...", schema: { ... } },
   *     strict: true
   *   }
   * }
   */
  private TextOptions text;

  /* ========= Tipos auxiliares ========= */

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class InputItem {
    /** "user" | "assistant" | "system" */
    private String role;

    /**
     * Lista de blocos de conteúdo:
     * - { "type":"input_text",  "text":"..." }
     * - { "type":"input_image", "image_url":"https://..." }
     * - { "type":"input_file",  "file_id":"file_..." }
     */
    private List<ContentBlock> content;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ContentBlock {
    /** "input_text" | "input_image" | "input_file" */
    private String type;

    /** Para "input_text" */
    private String text;

    /** Para "input_image" */
    @JsonProperty("image_url")
    private String imageUrl;

    /** Para "input_file" */
    @JsonProperty("file_id")
    private String fileId;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ToolDefinition {
    /**
     * "function" | "web_search" | "file_search" | "code_interpreter" | "computer"
     */
    private String type;

    /** Para function calling */
    private String name;

    /** Para function calling */
    private String description;

    /** JSON Schema dos parâmetros (para function calling) */
    private Map<String, Object> parameters;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class StreamOptions {
    /** Incluir uso (tokens) ao final do stream */
    @JsonProperty("include_usage")
    private Boolean includeUsage;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class TextOptions {
    /**
     * Formato da saída de texto.
     * Em Responses API o campo é "format" (não "response_format").
     */
    @JsonProperty("format")
    private ResponseFormat format;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ResponseFormat {
    /** "text" | "json_schema" (para structured outputs) */
    private String type;

    /** Definição do JSON Schema (quando type="json_schema") */
    @JsonProperty("json_schema")
    private JsonSchema jsonSchema;

    /** Enforce rígido do schema (opcional) */
    private Boolean strict;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class JsonSchema {
    private String name;
    /** Esquema JSON (Draft compatível) */
    private Map<String, Object> schema;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class PromptRef {
    private String id;
    private String version;
    private Map<String, Object> variables;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ReasoningOptions {
    /** "low" | "medium" | "high" (quando suportado pelo modelo) */
    private String effort;
  }

  public enum Truncation { AUTO, DISABLED }

  public enum Verbosity { LOW, MEDIUM, HIGH }

  /**
   * Camadas suportadas na Responses API.
   * Omitir a propriedade para comportamento automático padrão.
   */
  public enum ServiceTier {
    @JsonProperty("flex")
    FLEX,

    @JsonProperty("priority")
    PRIORITY
  }
}
