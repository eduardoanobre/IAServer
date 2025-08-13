package br.com.ia.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CacheKeys {

	  public static String compose(String chatId,
	                               String moduleKey,
	                               Integer rulesVersion,
	                               Integer schemaVersion,
	                               java.util.List<java.util.Map<String,Object>> shardList,
	                               String extraFacet) {
	    String ns = (moduleKey == null || moduleKey.isBlank()) ? "generic" : moduleKey;
	    int rv = rulesVersion == null ? 1 : rulesVersion;
	    int sv = schemaVersion == null ? 1 : schemaVersion;
	
	    // colecione (type, version) dos shards e ordene por type p/ determinismo
	    java.util.List<String> shardParts = new java.util.ArrayList<>();
	    if (shardList != null) {
	      java.util.List<String> pairs = new java.util.ArrayList<>();
	      for (var s : shardList) {
	        String t = String.valueOf(s.getOrDefault("type", ""));
	        int v = parseInt(s.get("version"), 1);
	        pairs.add(t + ":" + v);
	      }
	      java.util.Collections.sort(pairs);
	      for (String p : pairs) {
	        // vira sh:<type>-v<version>
	        int idx = p.lastIndexOf(':');
	        String t = p.substring(0, idx);
	        String v = p.substring(idx + 1);
	        shardParts.add("sh:" + t + "-v" + v);
	      }
	    }
	
	    String xf = (extraFacet == null || extraFacet.isBlank()) ? "" : "|x:" + sanitize(extraFacet);
	
	    // exemplo de formato final (simples e estável)
	    // scope:<chat>|mod:<ns>|rules:<rv>|schema:<sv>|sh:...|sh:...|x:...
	    return "scope:%s|mod:%s|rules:%d|schema:%d%s%s"
	        .formatted(chatId, ns, rv, sv, xf,
	          shardParts.isEmpty() ? "" : "|" + String.join("|", shardParts));
	  }
	
	  private static int parseInt(Object v, int def) {
	    try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return def; }
	  }
	
	  private static String sanitize(String in) {
	    return in.replaceAll("[^a-zA-Z0-9._:\\-|]", "_");
	  }
}
