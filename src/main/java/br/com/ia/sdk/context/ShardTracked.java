package br.com.ia.sdk.context;

import java.util.List;

public interface ShardTracked {

	/** Nome lógico do shard (ex.: "projeto", "sprint", "tarefa", "campanha"). */
	String shardType();

	/** Se o shard é estável (impacta caching/prefixo). Default: true. */
	default boolean shardStable() {
		return true;
	}

	/** Lista de nomes de campos da entidade que compõem o shard. */
	List<String> shardFields();

	/** Versão atual do shard (persistida na entidade). */
	Integer getShardVersion();

	void setShardVersion(Integer v);

	/** Fingerprint atual do shard (persistido na entidade). */
	String getShardFingerprint();

	void setShardFingerprint(String fp);

	/**
	 * para padronizar ContextShard da entidade
	 * 
	 */
	ContextShard obterContexShars();

}
