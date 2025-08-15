package br.com.ia.sdk.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import lombok.experimental.UtilityClass;

/**
 * Fábrica de {@link ContextShard}s para uso em prompts da Responses API.
 * Fornece métodos utilitários estáticos para criar shards estáveis/voláteis e
 * um helper para montar Map de payload de forma concisa.
 */
@UtilityClass
public final class ContextShards {

	public static final String TEXTO = "texto";
	public static final String TITULO = "titulo";
	public static final String VERSION = "version";
	public static final String TYPE = "type";

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

	/** Cria um shard efêmero/volátil (assinatura completa). */
	public static ContextShard ephemeral(String type, int version, boolean stable, Map<String, Object> payload) {
		return shard(type, version, stable, payload);
	}

	/** Cria um shard efêmero/volátil (stable=false por padrão). */
	public static ContextShard ephemeral(String type, int version, Map<String, Object> payload) {
		return shard(type, version, false, payload);
	}

	/** Cria um shard estável montando o payload pelos campos informados do bean. */
	public static ContextShard stableFrom(Object bean, String type, int version, String... fieldNames) {
		return shard(type, version, true, extract(bean, fieldNames));
	}

	/** Cria um shard efêmero montando o payload pelos campos informados do bean. */
	public static ContextShard ephemeralFrom(Object bean, String type, int version, String... fieldNames) {
		return shard(type, version, false, extract(bean, fieldNames));
	}

	private static ContextShard shard(String type, int version, boolean stable, Map<String, Object> payload) {
		final String t = Objects.requireNonNull(type, "type");
		final int v = version;
		final boolean st = stable;
		final Map<String, Object> p = payload == null ? Map.of() : Map.copyOf(payload);

		return new ContextShard() {
			@Override
			public String type() {
				return t;
			}

			@Override
			public int version() {
				return v;
			}

			@Override
			public boolean stable() {
				return st;
			}

			@Override
			public Map<String, Object> payload() {
				return p;
			}
		};
	}

	private static Map<String, Object> extract(Object bean, String... fields) {
		if (bean == null || fields == null || fields.length == 0)
			return Map.of();
		Map<String, Object> out = new LinkedHashMap<>();
		for (String f : fields)
			out.put(f, read(bean, f));
		return out;
	}

	private static Object read(Object bean, String field) {
		try {
			var m = bean.getClass().getMethod(getterName(field));
			return m.invoke(bean);
		} catch (NoSuchMethodException nsme) {
			try {
				var f = bean.getClass().getDeclaredField(field);
				f.setAccessible(true); // NOSONAR
				return f.get(bean);
			} catch (Exception ignore) {
				return null;
			}
		} catch (Exception e) {
			return null;
		}
	}

	private static String getterName(String field) {
		if (field == null || field.isBlank())
			return "get";
		String base = field.substring(0, 1).toUpperCase() + field.substring(1);
		return "get" + base;
	}
}
