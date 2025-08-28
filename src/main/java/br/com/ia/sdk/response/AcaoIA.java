package br.com.ia.sdk.response;

import java.util.List;

public record AcaoIA(
	    String metodo,
	    List<Object> dados
	) {}