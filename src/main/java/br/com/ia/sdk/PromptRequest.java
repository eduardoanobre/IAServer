package br.com.ia.sdk;

//imports necessários
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import br.com.ia.sdk.context.ContextShard;
import lombok.Data;

@Data
public class PromptRequest {

	private String type; // teste, command
	private String chatId;
	private String prompt;
	private String model;
	private String apiKey;
	private Double temperaturePercent;
	private int maxOutputTokens;
	private List<ContextShard> shards;
	private String instructions;
	private Map<String, Object> schema;
	private int instructionVersion;
	private int schemaVersion;
	private String cacheFacet;

	private ContextShard shardInstrucao;
	private ContextShard shardDescricao;
	private ContextShard shardObjetivo;
	private ContextShard shardEscopo;
	private ContextShard shardParticipantes;
	
	/**
	 * Identificador lógico do módulo/vertente (ex.: "workspace.projetos",
	 * "marketing.campanhas"). Ajuda a segmentar chave de cache entre módulos.
	 * <p>
	 * <b>Recomendado.</b>
	 * </p>
	 */
	private String moduleKey;

	// getters and setters

	public PromptRequest(PromptRequest src) {
		Objects.requireNonNull(src, "src não pode ser null");
		this.chatId = src.chatId;
		this.prompt = src.prompt;
		this.model = src.model;
		this.apiKey = src.apiKey;
		this.temperaturePercent = src.temperaturePercent;
		this.maxOutputTokens = src.maxOutputTokens;

		// nova lista, elementos por referência
		this.shards = (src.shards != null) ? new ArrayList<>(src.shards) : null;

		this.instructions = src.instructions;

		// novo mapa, valores por referência
		this.schema = (src.schema != null) ? new LinkedHashMap<>(src.schema) : null;

		this.instructionVersion = src.instructionVersion;
		this.schemaVersion = src.schemaVersion;

		// shards nomeados por referência
		this.shardInstrucao = src.shardInstrucao;
		this.shardDescricao = src.shardDescricao;
		this.shardObjetivo = src.shardObjetivo;
		this.shardEscopo = src.shardEscopo;
		this.shardParticipantes = src.shardParticipantes;
	}

	public static PromptRequest copyOf(PromptRequest src) {
		return new PromptRequest(src);
	}

	public PromptRequest() {
	}
}
