package br.com.ia.sdk.context;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import br.com.ia.sdk.context.dto.ContextShardDTO;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class ContextShardDTOs {

	public static List<ContextShardDTO> copyOf(List<?> shards) {
		return shards == null ? List.of() : shards.stream().map(ContextShardDTOs::of).toList();
	}

	public static ContextShardDTO of(Object shard) {
		String type = invokeString(shard, "getType", "type");
		String id = invokeString(shard, "getId", "id", "getUuid", "uuid");
		Integer version = invokeInteger(shard, "getVersion", "version", "getVer", "ver");
		Boolean stable = invokeBoolean(shard, "getStable", "stable", "isStable");
		@SuppressWarnings("unchecked")
		Map<String, Object> payload = (Map<String, Object>) invokeFirst(shard, "getPayload", "payload", "getData",
				"data", "getMap", "map");

		return new ContextShardDTO(type, id, version, stable, payload);
	}

	// ---------- helpers ----------
	private static Object invokeFirst(Object target, String... methods) {
		for (String m : methods) {
			Object v = tryInvoke(target, m);
			if (v != null)
				return v;
		}
		return null;
	}

	private static String invokeString(Object target, String... methods) {
		Object v = invokeFirst(target, methods);
		return (v instanceof String s) ? s : null;
	}

	private static Integer invokeInteger(Object target, String... methods) {
		Object v = invokeFirst(target, methods);
		if (v instanceof Integer i)
			return i;
		if (v instanceof Number n)
			return n.intValue();
		return null;
	}

	private static Boolean invokeBoolean(Object target, String... methods) {
		Object v = invokeFirst(target, methods);
		return (v instanceof Boolean b) ? b : null;
	}

	private static Object tryInvoke(Object target, String methodName) {
		try {
			Method m = target.getClass().getMethod(methodName);
			m.setAccessible(true);
			return m.invoke(target);
		} catch (ReflectiveOperationException ignore) {
			return null;
		}
	}
}
