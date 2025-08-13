package br.com.ia.sdk;

import java.util.List;
import java.util.Map;

import br.com.ia.sdk.context.ContextShard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>
 * DTO padronizado para solicitar a execução de um prompt via IAServer usando a
 * OpenAI <i>Responses API</i>. É consumido por {@code PromptExecutor}, que
 * normaliza parâmetros (ex.: temperatura), monta a requisição e envia para o
 * pipeline Kafka, aguardando o {@code IaResponse}.
 * </p>
 *
 * <h3>Uso típico</h3>
 * 
 * <pre>{@code
 * PromptRequest req = PromptRequest.builder().chatId(chatId) // UUID do projeto/escopo
 * 		.prompt(userPrompt) // mensagem do usuário
 * 		.instructions(instructions) // regras do módulo (system message)
 * 		.text(schemaStrict) // Structured Outputs (json_schema + strict:true)
 * 		.model("gpt-5").apiKey(apiKey).temperaturePercent(25.0) // 0..100 (IAServer converte para 0..2)
 * 		.maxOutputTokens(1000).versaoRegrasModulo(3) // versão do texto de regras do módulo
 * 		.versaoInstrucaoProjeto(12) // versão da instrução específica do projeto
 * 		.versaoSchema(1) // versão do JSON Schema
 * 		.contextShards(shards) // shards de contexto (projeto/sprint/tarefa/etc.)
 * 		.cacheFacet("risk-global") // (opcional) segmentação extra de cache
 * 		.build();
 * }</pre>
 *
 * <h4>Notas</h4>
 * <ul>
 * <li><b>Temperatura:</b> informe em escala 0..100; o IAServer converte
 * linearmente para 0..2 com clamp e usa um default (~0.3) se ausente/
 * inválido.</li>
 * <li><b>Cache (prompt_cache_key):</b> o IAServer compõe a chave usando
 * {@link #chatId}, {@link #versaoRegrasModulo},
 * {@link #versaoInstrucaoProjeto}, {@link #versaoSchema} e, se informado,
 * {@link #cacheFacet}. Assim, mudar apenas o texto de regras do módulo
 * <em>ou</em> apenas a instrução do projeto <em>ou</em> apenas o schema
 * invalida o cache de forma precisa.</li>
 * <li><b>Structured Outputs:</b> em {@link #text}, envie o objeto equivalente a
 * {@code { response_format: { type: "json_schema", json_schema: {...}, strict:
 * true } }} para garantir que a saída será um único JSON válido no formato
 * esperado.</li>
 * <li><b>Shards de contexto:</b> {@link #contextShards} permite enviar blocos
 * de contexto versionados (projeto/sprint/tarefa/etc.). O IAServer ordena,
 * serializa e acrescenta ao input do modelo antes do {@link #prompt} do
 * usuário.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptRequest {

	/**
	 * Identificador único do chat/escopo (tipicamente o UUID do projeto). Também
	 * usado como safety_identifier.
	 */
	private String chatId;

	/** Mensagem do usuário enviada ao modelo (conteúdo dinâmico por interação). */
	private String prompt;

	/**
	 * Mensagem de sistema (regras/políticas do módulo). Versione via
	 * {@link #versaoRegrasModulo}.
	 */
	private String instructions;

	/**
	 * Structured Outputs para JSON estrito. Exemplo:
	 * 
	 * <pre>
	 * {
	 *   "response_format": {
	 *     "type": "json_schema",
	 *     "json_schema": { ... },
	 *     "strict": true
	 *   }
	 * }
	 * </pre>
	 */
	private Map<String, Object> text;

	/** Modelo alvo (ex.: "gpt-5", "gpt-5-mini"). */
	private String model;

	/** Chave de API do provedor (override por requisição). */
	private String apiKey;

	/**
	 * Temperatura em escala 0..100; o IAServer converte para 0..2 com clamp e
	 * default (~0.3).
	 */
	private Double temperaturePercent;

	/** Limite superior de tokens de saída do modelo. */
	private Integer maxOutputTokens;

	/**
	 * Versão da instrução específica do projeto (system prompt específico do
	 * projeto). Impacta cache.
	 */
	private Integer versaoInstrucaoProjeto;

	/** Versão do JSON Schema (Structured Outputs). Impacta cache. */
	private Integer versaoSchema;

	/**
	 * Shards de contexto (projeto/sprint/tarefa/etc.) a serem enviados antes do
	 * prompt do usuário.
	 */
	private List<ContextShard> contextShards;

	/**
	 * (Opcional) Faceta extra para segmentar cache por operação/caso de uso (ex.:
	 * "risk-global").
	 */
	private String cacheFacet;
}
