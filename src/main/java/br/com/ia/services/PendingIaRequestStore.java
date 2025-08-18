package br.com.ia.services;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.ia.model.IaResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PendingIaRequestStore {

	private final Map<String, CompletableFuture<IaResponse>> pending = new ConcurrentHashMap<>();
	private final Map<String, LocalDateTime> timestamps = new ConcurrentHashMap<>();

	private ScheduledExecutorService cleanupScheduler;

	@Value("${erp.ia.reply-timeout-ms:30000}")
	private long defaultTimeoutMs;

	@Value("${erp.ia.cleanup-interval-minutes:5}")
	private long cleanupIntervalMinutes;

	@Value("${erp.ia.max-pending-requests:1000}")
	private int maxPendingRequests;

	@PostConstruct
	public void init() {
		// Inicializa scheduler para limpeza automática de requests órfãos
		cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "PendingIaRequestStore-Cleanup");
			t.setDaemon(true);
			return t;
		});

		// Executa limpeza a cada X minutos
		cleanupScheduler.scheduleWithFixedDelay(this::cleanupExpiredRequests, cleanupIntervalMinutes,
				cleanupIntervalMinutes, TimeUnit.MINUTES);

		log.info("✅ PendingIaRequestStore inicializado com limpeza automática a cada {} minutos",
				cleanupIntervalMinutes);
	}

	@PreDestroy
	public void destroy() {
		if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
			cleanupScheduler.shutdown();
			try {
				if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
					cleanupScheduler.shutdownNow();
				}
			} catch (InterruptedException e) {
				cleanupScheduler.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}

		// Cancela todos os futures pendentes
		cancelAllPending("Application shutdown");

		log.info("🛑 PendingIaRequestStore destruído");
	}

	/**
	 * Cria um novo CompletableFuture para aguardar resposta
	 *
	 * @param idChat ID do chat
	 * @return CompletableFuture que será resolvido quando a resposta chegar
	 */
	public CompletableFuture<IaResponse> create(String idChat) {
		if (idChat == null || idChat.trim().isEmpty()) {
			throw new IllegalArgumentException("idChat não pode ser nulo ou vazio");
		}

		// Verifica limite de requests pendentes
		if (pending.size() >= maxPendingRequests) {
			log.warn("⚠️ Limite de requests pendentes atingido: {}", maxPendingRequests);
			cleanupExpiredRequests(); // Tenta limpar expirados primeiro

			if (pending.size() >= maxPendingRequests) {
				throw new IllegalStateException("Muitos requests pendentes: " + pending.size());
			}
		}

		// Remove future anterior se existir (cleanup)
		if (pending.containsKey(idChat)) {
			log.warn("⚠️ Substituindo future existente para chatId: {}", idChat);
			cancel(idChat);
		}

		var future = new CompletableFuture<IaResponse>();
		pending.put(idChat, future);
		timestamps.put(idChat, LocalDateTime.now());

		log.debug("📝 Future criado para chatId: {} (total pendentes: {})", idChat, pending.size());
		return future;
	}

	/**
	 * Completa um future pendente com a resposta
	 *
	 * @param idChat   ID do chat
	 * @param response Resposta da IA
	 * @return true se havia um future pendente, false caso contrário
	 */
	public boolean complete(String idChat, IaResponse response) {
		if (idChat == null) {
			return false;
		}

		var future = pending.remove(idChat);
		timestamps.remove(idChat);

		if (future != null) {
			if (!future.isDone()) {
				future.complete(response);
				log.debug("✅ Future completado para chatId: {} (restantes: {})", idChat, pending.size());
				return true;
			} else {
				log.warn("⚠️ Tentativa de completar future já finalizado: {}", idChat);
				return false;
			}
		}

		log.debug("❓ Tentativa de completar future inexistente: {}", idChat);
		return false;
	}

	/**
	 * Completa um future pendente com erro
	 *
	 * @param idChat ID do chat
	 * @param ex     Exceção ocorrida
	 * @return true se havia um future pendente, false caso contrário
	 */
	public boolean fail(String idChat, Throwable ex) {
		if (idChat == null) {
			return false;
		}

		var future = pending.remove(idChat);
		timestamps.remove(idChat);

		if (future != null) {
			if (!future.isDone()) {
				future.completeExceptionally(ex);
				log.debug("❌ Future falhado para chatId: {} - {}", idChat, ex.getMessage());
				return true;
			} else {
				log.warn("⚠️ Tentativa de falhar future já finalizado: {}", idChat);
				return false;
			}
		}

		log.debug("❓ Tentativa de falhar future inexistente: {}", idChat);
		return false;
	}

	/**
	 * Cancela um future pendente
	 *
	 * @param chatId ID do chat
	 * @return true se havia um future pendente, false caso contrário
	 */
	public boolean cancel(String chatId) {
		if (chatId == null) {
			return false;
		}

		var future = pending.remove(chatId);
		timestamps.remove(chatId);

		if (future != null) {
			if (!future.isDone()) {
				boolean cancelled = future.cancel(true);
				log.debug("🚫 Future cancelado para chatId: {} (sucesso: {})", chatId, cancelled);
				return cancelled;
			} else {
				log.debug("⚠️ Tentativa de cancelar future já finalizado: {}", chatId);
				return false;
			}
		}

		log.debug("❓ Tentativa de cancelar future inexistente: {}", chatId);
		return false;
	}

	/**
	 * Remove um future pendente sem completar (cleanup silencioso)
	 *
	 * @param idChat ID do chat
	 * @return true se havia um future pendente, false caso contrário
	 */
	public boolean remove(String idChat) {
		if (idChat == null) {
			return false;
		}

		var future = pending.remove(idChat);
		timestamps.remove(idChat);

		if (future != null) {
			log.debug("🗑️ Future removido para chatId: {}", idChat);
			return true;
		}
		return false;
	}

	/**
	 * Verifica se existe um future pendente
	 *
	 * @param idChat ID do chat
	 * @return true se existe, false caso contrário
	 */
	public boolean exists(String idChat) {
		return idChat != null && pending.containsKey(idChat);
	}

	/**
	 * Retorna o número de requests pendentes
	 *
	 * @return número de futures pendentes
	 */
	public int size() {
		return pending.size();
	}

	/**
	 * Cancela todos os futures pendentes com uma mensagem específica
	 *
	 * @param reason Motivo do cancelamento
	 */
	public void cancelAllPending(String reason) {
		if (pending.isEmpty()) {
			return;
		}

		log.warn("🚫 Cancelando {} futures pendentes. Motivo: {}", pending.size(), reason);

		pending.forEach((chatId, future) -> {
			if (!future.isDone()) {
				future.completeExceptionally(new RuntimeException("Request cancelado: " + reason));
			}
		});

		pending.clear();
		timestamps.clear();

		log.info("✅ Todos os futures foram cancelados");
	}

	/**
	 * Remove requests expirados (órfãos)
	 */
	public void cleanupExpiredRequests() {
		if (timestamps.isEmpty()) {
			return;
		}

		LocalDateTime cutoff = LocalDateTime.now().minusSeconds(defaultTimeoutMs / 1000 * 2); // 2x timeout
		int removed = 0;

		var iterator = timestamps.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			String chatId = entry.getKey();
			LocalDateTime timestamp = entry.getValue();

			if (timestamp.isBefore(cutoff)) {
				var future = pending.get(chatId);
				if (future != null && !future.isDone()) {
					future.completeExceptionally(new RuntimeException("Request expirado (timeout)"));
				}

				pending.remove(chatId);
				iterator.remove();
				removed++;
			}
		}

		if (removed > 0) {
			log.info("🧹 Limpeza automática: {} requests expirados removidos (restantes: {})", removed, pending.size());
		}
	}

	/**
	 * Retorna estatísticas do store
	 *
	 * @return Map com estatísticas
	 */
	public Map<String, Object> getStatistics() {
		long completed = pending.values().stream().mapToLong(f -> f.isDone() ? 1L : 0L).sum();

		return Map.of("totalPending", pending.size(), "completedButNotRemoved", completed, "activePending",
				pending.size() - completed, "maxAllowed", maxPendingRequests, "defaultTimeoutMs", defaultTimeoutMs);
	}
}