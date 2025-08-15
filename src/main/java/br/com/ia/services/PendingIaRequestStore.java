package br.com.ia.services;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import br.com.ia.model.IaResponse;

@Component
public class PendingIaRequestStore {

	private final Map<String, CompletableFuture<IaResponse>> pending = new ConcurrentHashMap<>();

	/**
	 * Cria um novo CompletableFuture para aguardar resposta
	 * 
	 * @param idChat ID do chat
	 * @return CompletableFuture que será resolvido quando a resposta chegar
	 */
	public CompletableFuture<IaResponse> create(String idChat) {
		var future = new CompletableFuture<IaResponse>();
		pending.put(idChat, future);
		return future;
	}

	/**
	 * Completa um future pendente com a resposta
	 * 
	 * @param idChat   ID do chat
	 * @param response Resposta da IA
	 * @return true se havia um future pendente, false caso contrario
	 */
	public boolean complete(String idChat, IaResponse response) {
		var future = pending.remove(idChat);
		if (future != null) {
			future.complete(response);
			return true;
		}
		return false;
	}

	/**
	 * Completa um future pendente com erro
	 * 
	 * @param idChat ID do chat
	 * @param ex     Excecao ocorrida
	 * @return true se havia um future pendente, false caso contrario
	 */
	public boolean fail(String idChat, Throwable ex) {
		var future = pending.remove(idChat);
		if (future != null) {
			future.completeExceptionally(ex);
			return true;
		}
		return false;
	}

	/**
	 * Remove um future pendente sem completar
	 * 
	 * @param idChat ID do chat
	 * @return true se havia um future pendente, false caso contrario
	 */
	public boolean remove(String idChat) {
		return pending.remove(idChat) != null;
	}

	/**
	 * Verifica se existe um future pendente
	 * 
	 * @param idChat ID do chat
	 * @return true se existe, false caso contrario
	 */
	public boolean exists(String idChat) {
		return pending.containsKey(idChat);
	}

	/**
	 * Retorna o numero de requests pendentes
	 * 
	 * @return numero de futures pendentes
	 */
	public int size() {
		return pending.size();
	}
}