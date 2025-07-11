package br.com.ia.services;

import br.com.ia.model.ChatCompletionRequest;

public interface IAClient {
	String call(ChatCompletionRequest request);
}
