package br.com.ia.services;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.ia.sdk.response.RespostaIA;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingIaRequestStore {

	private final Map<String, CompletableFuture<RespostaIA>> pending = new ConcurrentHashMap<>();
	/** Guarda o instante de criação do Future em epoch millis. */
	private final Map<String, Long> timestamps = new ConcurrentHashMap<>();

	private ScheduledExecutorService cleanupScheduler;

	@Value("${ia.pending.reply-timeout-ms:30000}")
	private long defaultTimeoutMs;

	@Value("${ia.pending.cleanup-interval-minutes:5}")
	private long cleanupIntervalMinutes;

	@Value("${ia.pending.max-pending-requests:1000}")
	private int maxPendingRequests;

	@PostConstruct
	public void init() {
		log.info("⚙️ Configuração PendingIaRequestStore carregada:");
		log.info("   ➤ ia.pending.reply-timeout-ms         = {} ms", defaultTimeoutMs);
		log.info("   ➤ ia.pending.cleanup-interval-minutes = {} min", cleanupIntervalMinutes);
		log.info("   ➤ ia.pending.max-pending-requests     = {}", maxPendingRequests);

		cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "PendingIaRequestStore-Cleanup");
			t.setDaemon(true);
			return t;
		});

		// limpeza periódica (atraso inicial = intervalo)
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
		cancelAllPending("Application shutdown");
		log.info("🛑 PendingIaRequestStore destruído");
	}

	/** Cria (ou substitui) o future de um idRequest. */
	public CompletableFuture<RespostaIA> create(Long idRequest) {
		if (idRequest == null) {
			throw new IllegalArgumentException("idRequest não pode ser nulo");
		}

		if (pending.size() >= maxPendingRequests) {
			log.warn("⚠️ Limite de requests pendentes atingido: {}. Tentando limpeza de expirados…",
					maxPendingRequests);
			cleanupExpiredRequests();
			if (pending.size() >= maxPendingRequests) {
				throw new IllegalStateException("Muitos requests pendentes: " + pending.size());
			}
		}

		final String key = idRequest.toString();

		// Se já existe, cancela o anterior para não vazar Future
		CompletableFuture<RespostaIA> previous = pending.remove(key);
		if (previous != null && !previous.isDone()) {
			previous.completeExceptionally(new RuntimeException("Request substituído por outro com mesmo id"));
			log.warn("⚠️ Future existente substituído para idRequest: {}", idRequest);
		}
		timestamps.remove(key);

		var future = new CompletableFuture<RespostaIA>();
		pending.put(key, future);
		timestamps.put(key, System.currentTimeMillis());

		log.debug("[PENDING-IA] Future criado para idRequest: {} (total pendentes: {})", idRequest, pending.size());
		return future;
	}

	/** Completa o future e remove controles internos. */
	public boolean complete(Long idRequest, RespostaIA response) {
		if (idRequest == null)
			return false;

		final String key = idRequest.toString();
		var future = pending.remove(key);
		timestamps.remove(key);

		if (future != null) {
			if (!future.isDone()) {
				future.complete(response);
				log.debug("[PENDING-IA] ✅ Future completado para idRequest: {} (restantes: {})", idRequest,
						pending.size());
				return true;
			} else {
				log.warn("[PENDING-IA] ⚠️ Tentativa de completar future já finalizado: {}", idRequest);
				return false;
			}
		}
		log.debug("[PENDING-IA] ❓ Tentativa de completar future inexistente: {}", idRequest);
		return false;
	}

	/** Falha o future e remove controles internos. */
	public boolean fail(Long idRequest, Throwable ex) {
		if (idRequest == null)
			return false;

		final String key = idRequest.toString();
		var future = pending.remove(key);
		timestamps.remove(key);

		if (future != null) {
			if (!future.isDone()) {
				future.completeExceptionally(ex != null ? ex : new RuntimeException("Falha desconhecida"));
				log.debug("[PENDING-IA] ❌ Future falhado para idRequest: {} - {}", idRequest,
						ex != null ? ex.getMessage() : "(null)");
				return true;
			} else {
				log.warn("[PENDING-IA] ⚠️ Tentativa de falhar future já finalizado: {}", idRequest);
				return false;
			}
		}
		log.debug("[PENDING-IA] ❓ Tentativa de falhar future inexistente: {}", idRequest);
		return false;
	}

	/** Cancela o future e remove controles internos. */
	public boolean cancel(Long idRequest) {
		if (idRequest == null)
			return false;

		final String key = idRequest.toString();
		var future = pending.remove(key);
		timestamps.remove(key);

		if (future != null) {
			if (!future.isDone()) {
				boolean cancelled = future.cancel(true);
				log.debug("[PENDING-IA] 🚫 Future cancelado para idRequest: {} (sucesso: {})", idRequest, cancelled);
				return cancelled;
			} else {
				log.debug("[PENDING-IA] ⚠️ Tentativa de cancelar future já finalizado: {}", idRequest);
				return false;
			}
		}
		log.debug("[PENDING-IA] ❓ Tentativa de cancelar future inexistente: {}", idRequest);
		return false;
	}

	/** Remove o future e timestamp sem completar/cancelar. */
	public boolean remove(Long idRequest) {
		if (idRequest == null)
			return false;

		final String key = idRequest.toString();
		var future = pending.remove(key);
		timestamps.remove(key);

		if (future != null) {
			log.debug("[PENDING-IA] 🗑️ Future removido para idRequest: {}", idRequest);
			return true;
		}
		return false;
	}

	public boolean exists(Long idRequest) {
		return idRequest != null && pending.containsKey(idRequest.toString());
	}

	public int size() {
		return pending.size();
	}

	/** Cancela todos os pendentes (completeExceptionally) e limpa estruturas. */
	public void cancelAllPending(String reason) {
		if (pending.isEmpty())
			return;

		log.warn("[PENDING-IA] 🚫 Cancelando {} futures pendentes. Motivo: {}", pending.size(), reason);
		pending.forEach((idReq, future) -> {
			if (!future.isDone()) {
				future.completeExceptionally(new RuntimeException("Request cancelado: " + reason));
			}
		});
		pending.clear();
		timestamps.clear();
		log.info("[PENDING-IA] ✅ Todos os futures foram cancelados");
	}

	/** Limpeza periódica de requests claramente “abandonados”. */
	public void cleanupExpiredRequests() {
		if (timestamps.isEmpty())
			return;

		final long now = System.currentTimeMillis();
		final long cutoff = now - (defaultTimeoutMs * 2); // 2x timeout como margem
		int removed = 0;

		var it = timestamps.entrySet().iterator();
		while (it.hasNext()) {
			var entry = it.next();
			final String idRequest = entry.getKey();
			final long createdAt = entry.getValue();

			if (createdAt < cutoff) {
				var future = pending.remove(idRequest);
				if (future != null && !future.isDone()) {
					future.completeExceptionally(new RuntimeException("Request expirado (timeout)"));
				}
				it.remove();
				removed++;
			}
		}

		if (removed > 0) {
			log.info("[PENDING-IA] 🧹 Limpeza: {} requests expirados removidos (restantes: {})", removed,
					pending.size());
		}
	}

	/** Estatísticas rápidas para métricas/diagnóstico. */
	public Map<String, Object> getStatistics() {
		long completed = pending.values().stream().filter(CompletableFuture::isDone).count();
		long now = System.currentTimeMillis();
		long oldestAgeMs = timestamps.values().stream().mapToLong(ts -> now - ts).max().orElse(0L);

		return Map.of("totalPending", pending.size(), "completedButNotRemoved", completed, "activePending",
				pending.size() - completed, "maxAllowed", maxPendingRequests, "defaultTimeoutMs", defaultTimeoutMs,
				"oldestAgeMs", oldestAgeMs, "now", Instant.ofEpochMilli(now).toString());
	}

	/** Helper sem ruído de log “workspace”. */
	public void completePendingRequest(Long idRequest, RespostaIA respostaIA) {
		if (exists(idRequest)) {
			complete(idRequest, respostaIA);
			log.info("[PENDING-IA] *** COMPLETED PENDING FUTURE *** idRequest: {}", idRequest);
		} else {
			log.info("[PENDING-IA] Nenhum future pendente para idRequest: {} (processamento assíncrono)", idRequest);
		}
	}
}
