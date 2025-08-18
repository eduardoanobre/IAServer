package br.com.ia.sdk;

import br.com.ia.sdk.context.ContextShard;

public interface IShardsRequest {

	ContextShard getShardInstrucao();
	ContextShard getShardDescricao();
	ContextShard getShardObjetivo();
	ContextShard getShardEscopo();
	ContextShard getShardParticipantes();
}
