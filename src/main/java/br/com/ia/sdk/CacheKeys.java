package br.com.ia.sdk;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class CacheKeys {

	/** Ex.: ws:<chatId>:instr-v<instr>:schema-v<schema> */
	public static String forProjeto(String chatId, int instrVersion, int schemaVersion) {
		return "ws:%s:instr-v%d:schema-v%d".formatted(chatId, instrVersion, schemaVersion);
	}

}
