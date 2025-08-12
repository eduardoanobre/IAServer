package br.com.ia.sdk;

public final class Temperature {
	private Temperature() {
	}

	public static final double DEFAULT = 0.3;

	/** Converte 0..100 → 0..2 com clamp e arredondamento. */
	public static double fromPercent(Object raw, double def) {
		if (raw == null)
			return def;
		double v;
		if (raw instanceof Number n)
			v = n.doubleValue();
		else {
			try {
				v = Double.parseDouble(String.valueOf(raw).trim());
			} catch (Exception e) {
				return def;
			}
		}
		if (!Double.isFinite(v))
			return def;
		v = Math.max(0.0, Math.min(100.0, v)); // clamp
		double scaled = (v / 100.0) * 2.0; // 0..100 -> 0..2
		return Math.round(scaled * 1000.0) / 1000.0;
	}
}
