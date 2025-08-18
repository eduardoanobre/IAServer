package br.com.ia.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FiltroUtils {

	public static String filtroLike(String filtro) {
		return isBlank(filtro) ? "%" : "%" + filtro.trim() + "%";
	}

	public static boolean isBlank(String filtro) {
		return filtro == null || filtro.isEmpty();
	}
}
