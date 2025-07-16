package br.com.ia.services.client;

import br.com.ia.model.ChatCompletionRequest;
import br.com.shared.exception.IAException;

public interface IAIClient {

	IaCallResult call(ChatCompletionRequest request) throws IAException;

}