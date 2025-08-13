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
 * 		.moduleKey("workspace.projetos") // chave do módulo para escopo de cache
 * 		.prompt(userPrompt) // mensagem do usuário
 * 		.instructions(instructions) // regras do módulo (system message)
 * 		.text(schemaStrict) // Structured Outputs (json_schema + strict:true)
 * 		.model("gpt-5").apiKey(apiKey).temperaturePercent(25.0) // 0..100 (IAServer converte para 0..2)
 * 		.maxOutputTokens(1000).versaoRegrasModulo(3) // versão do texto de regras do módulo
 * 		.versaoInstrucaoProjeto(12) // (opcional/telemetria) versão da instrução do projeto
 * 		.versaoSchema(1) // versão do JSON Schema
 * 		.contextShards(shards) // shards de contexto (projeto/sprint/tarefa/etc.)
 * 		.cacheFacet("risk-global") // (opcional) facet extra de cache
 * 		.build();
 * }</pre>
 *
 * <h4>Notas</h4>
 * <ul>
 * <li><b>Temperatura:</b> informe em escala 0..100; o IAServer converte
 * linearmente para 0..2 com clamp e usa um default (~0.3) se ausente/
 * inválido.</li>
 * <li><b>Cache (prompt_cache_key):</b> o IAServer compõe a chave usando
 * {@link #chatId}, {@link #moduleKey}, {@link #versaoRegrasModulo},
 * {@link #versaoSchema}, as versões dos {@link #contextShards} (type/version)
 * e, se informado, {@link #cacheFacet}. Assim, alterar somente as regras do
 * módulo, somente o schema, ou somente algum shard (ex.: objetivo, escopo,
 * participantes) invalida o cache de forma precisa.</li>
 * <li><b>Structured Outputs:</b> em {@link #text}, envie o objeto equivalente a
 * {@code { response_format: { type: "json_schema", json_schema: {...}, strict:
 * true } }} para garantir que a saída será um único JSON válido no formato
 * esperado.</li>
 * <li><b>Shards de contexto:</b> {@link #contextShards} permite enviar blocos
 * de contexto versionados (projeto/sprint/tarefa/etc.). O IAServer ordena,
 * serializa e inclui no input do modelo antes do {@link #prompt} do
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
	 * usado como <i>safety_identifier</i>.
	 * <p>
	 * <b>Obrigatório.</b>
	 * </p>
	 */
	private String chatId;

	/**
	 * Identificador lógico do módulo/vertente (ex.: "workspace.projetos",
	 * "marketing.campanhas"). Ajuda a segmentar chave de cache entre módulos.
	 * <p>
	 * <b>Recomendado.</b>
	 * </p>
	 */
	private String moduleKey;

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
	 * Versão das regras do módulo (system message do módulo). Impacta o cache
	 * global do módulo.
	 */
	private Integer versaoRegrasModulo;

	/**
	 * Versão da instrução específica do projeto (system prompt específico do
	 * projeto). <br/>
	 * Opcional (telemetria/relato). Por padrão, o cache ***não*** depende
	 * diretamente deste campo; a recomendação é versionar os textos do projeto via
	 * {@link #contextShards} (ex.: shards "projeto.instrucao",
	 * "projeto.objetivo"...).
	 */
	private Integer versaoInstrucaoProjeto;

	/** Versão do JSON Schema (Structured Outputs). Impacta cache. */
	private Integer versaoSchema;

	/**
	 * Shards de contexto (projeto/sprint/tarefa/etc.) a serem enviados antes do
	 * prompt do usuário. Suas versões (type/version) entram na composição da chave
	 * de cache.
	 */
	private List<ContextShard> contextShards;

	/**
	 * (Opcional) Faceta extra para segmentar cache por operação/caso de uso (ex.:
	 * "risk-global", "tasks-sprint-3").
	 */
	private String cacheFacet;
}
