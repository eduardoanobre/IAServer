package br.com.ia.services.client;

import br.com.ia.model.ChatCompletionRequest;

public interface IAIClient {
	String call(ChatCompletionRequest request);
}
