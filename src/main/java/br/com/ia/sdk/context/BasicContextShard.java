package br.com.ia.sdk.context;

import java.util.Map;

public record BasicContextShard(
	    String type,
	    int version,
	    boolean stable,
	    Map<String, Object> payload
	) implements ContextShard {}
