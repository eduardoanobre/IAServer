package br.com.ia.services;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import br.com.ia.model.IaResponse;

@Component
public class PendingIaRequestStore {

	private final Map<String, CompletableFuture<IaResponse>> pending = new ConcurrentHashMap<>();

	public CompletableFuture<IaResponse> create(String idChat) {
		var future = new CompletableFuture<IaResponse>();
		pending.put(idChat, future);
		return future;
	}

	public void complete(String idChat, IaResponse response) {
		var future = pending.remove(idChat);
		if (future != null) {
			future.complete(response);
		}
	}

	public void fail(String idChat, Throwable ex) {
		var future = pending.remove(idChat);
		if (future != null) {
			future.completeExceptionally(ex);
		}
	}

	public void remove(String idChat) {
		pending.remove(idChat);
	}

	public boolean exists(String idChat) {
		return pending.containsKey(idChat);
	}
}
