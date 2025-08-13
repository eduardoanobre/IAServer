package br.com.ia.sdk.context;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class CacheKeys {
	  	  
	  public static String forProjeto(String chatId, Integer instrV, Integer schemaV, String facet) {
		    int iV = (instrV == null ? 1 : instrV);
		    int sV = (schemaV == null ? 1 : schemaV);
		    String f = (facet == null || facet.isBlank()) ? "none" : facet;
		    return "ws:%s:instr-v%d:facet-%s:schema-v%d".formatted(chatId, iV, f, sV);
		  }

}
