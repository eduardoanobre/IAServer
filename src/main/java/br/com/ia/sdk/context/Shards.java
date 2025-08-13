package br.com.ia.sdk.context;

import java.util.Map;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class Shards {

	public static ContextShard stable(String type, int version, Map<String, Object> payload) {
		return shard(type, version, true, payload);
	}

	public static ContextShard volatileShard(String type, int version, Map<String, Object> payload) {
		return shard(type, version, false, payload);
	}

	public static ContextShard shard(String type, int version, boolean stable, Map<String, Object> payload) {
		return new ContextShard() {
			public String type() {
				return type;
			}

			public int version() {
				return version;
			}

			public boolean stable() {
				return stable;
			}

			public Map<String, Object> payload() {
				return payload;
			}
		};
	}
}
