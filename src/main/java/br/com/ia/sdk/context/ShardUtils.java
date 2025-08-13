package br.com.ia.sdk.context;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ShardUtils {

	private ShardUtils() {
	}

	/**
	 * Cria um ContextShard estável/volátil a partir de uma entidade ShardTracked.
	 */
	public static ContextShard toShard(ShardTracked st) {
		int version = st.getShardVersion() == null ? 1 : st.getShardVersion();
		Map<String, Object> payload = payloadMap(st, st.shardFields());
		return st.shardStable() ? Shards.stable(st.shardType(), version, payload)
				: Shards.volatileShard(st.shardType(), version, payload);
	}

	/** Mapeia campos -> valores por reflexão simples (getters convencionais). */
	public static Map<String, Object> payloadMap(Object bean, List<String> fieldNames) {
		Map<String, Object> out = new LinkedHashMap<>();
		if (bean == null || fieldNames == null)
			return out;

		Class<?> c = bean.getClass();
		for (String f : fieldNames) {
			try {
				String getter = "get" + Character.toUpperCase(f.charAt(0)) + f.substring(1);
				Method m = c.getMethod(getter);
				Object v = m.invoke(bean);
				out.put(f, v);
			} catch (Exception ignore) {
				// fallback: tenta isX() (boolean)
				try {
					String is = "is" + Character.toUpperCase(f.charAt(0)) + f.substring(1);
					Method m2 = c.getMethod(is);
					Object v2 = m2.invoke(bean);
					out.put(f, v2);
				} catch (Exception e2) {
					out.put(f, null); // não quebra shard se campo não acessível
				}
			}
		}
		return out;
	}

	/** Fingerprint determinístico (SHA-256) apenas dos campos relevantes. */
	public static String fingerprintByFields(Object bean, List<String> fieldNames) {
		Map<String, Object> map = payloadMap(bean, fieldNames);

		// ordena por chave para estabilidade
		List<String> keys = new ArrayList<>(map.keySet());
		Collections.sort(keys);

		StringBuilder sb = new StringBuilder();
		for (String k : keys) {
			Object v = map.get(k);
			sb.append(k).append('=').append(safe(v)).append('\n');
		}
		return sha256Hex(sb.toString());
	}

	private static String safe(Object o) {
		return (o == null) ? "null" : Objects.toString(o).trim();
	}

	private static String sha256Hex(String s) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(64);
			for (byte b : dig)
				sb.append(String.format("%02x", b));
			return sb.toString();
		} catch (Exception e) {
			return Integer.toHexString(s.hashCode());
		}
	}
}
