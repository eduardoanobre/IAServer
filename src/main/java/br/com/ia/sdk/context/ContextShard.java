package br.com.ia.sdk.context;

import java.util.Map;

public interface ContextShard {
	
	/** Ex.: "projeto", "sprint", "tarefa", "campanha", "audiencia", "fatura"... */
	String type();

	/** Versão do shard (mude quando o conteúdo-base estável mudar) */
	int version();

	/** true = shard estável (bom para cache de prefixo); false = volátil */
	boolean stable();

	/** Payload leve já pronto p/ prompt (será serializado em JSON) */
	Map<String, Object> payload();
	
}
