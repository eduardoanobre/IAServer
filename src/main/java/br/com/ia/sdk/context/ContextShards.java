package br.com.ia.sdk.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fábrica de {@link ContextShard}s para uso em prompts da Responses API.
 * Fornece métodos utilitários estáticos para criar shards estáveis/voláteis e
 * um helper para montar Map de payload de forma concisa.
 */
public final class ContextShards {

	private ContextShards() {
	}

	/** Implementação imutável e leve de ContextShard. */
	private record ShardImpl(String type, int version, boolean stable, Map<String, Object> payload)
			implements ContextShard {
	}

	/**
	 * Cria um shard genérico.
	 *
	 * @param type    ex.: "projeto", "sprint", "tarefa"
	 * @param version versão do shard (>= 0)
	 * @param stable  true para shard estável (bom p/ cache de prefixo)
	 * @param payload conteúdo leve (será copiado e tornado imutável)
	 */
	public static ContextShard of(String type, int version, boolean stable, Map<String, Object> payload) {
		if (type == null || type.isBlank()) {
			throw new IllegalArgumentException("type é obrigatório");
		}
		if (version < 0) {
			throw new IllegalArgumentException("version deve ser >= 0");
		}
		Map<String, Object> copy = (payload == null) ? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(payload));
		return new ShardImpl(type, version, stable, copy);
	}

	/** Atalho: shard estável. */
	public static ContextShard stable(String type, int version, Map<String, Object> payload) {
		return of(type, version, true, payload);
	}

	/** Atalho: shard volátil. */
	public static ContextShard volatileShard(String type, int version, Map<String, Object> payload) {
		return of(type, version, false, payload);
	}

	/**
	 * Helper para montar um Map de forma concisa. Uso:
	 * {@code mapOf("id", 123, "nome", "Projeto X")}
	 */
	public static Map<String, Object> mapOf(Object... kv) {
		if (kv == null || kv.length == 0)
			return Map.of();
		if ((kv.length & 1) != 0) {
			throw new IllegalArgumentException("mapOf requer número PAR de argumentos (chave, valor, ...)");
		}
		Map<String, Object> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			String key = String.valueOf(kv[i]);
			Object val = kv[i + 1];
			m.put(key, val);
		}
		return m;
	}
}
