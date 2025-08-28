package br.com.ia.processor.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DuplicateMessageGuard {

    private final Set<String> processedMessageHashes = ConcurrentHashMap.newKeySet();

    public String computeHash(Object input) {
        try {
            String s = String.valueOf(input);
            if (s.length() > 100) s = s.substring(0, 100);
            return Integer.toHexString(s.hashCode());
        } catch (Exception e) {
            return "hash-error-" + System.nanoTime();
        }
    }

    public boolean isDuplicate(String hash) {
        boolean dup = processedMessageHashes.contains(hash);
        if (!dup && processedMessageHashes.size() > 1000) {
            processedMessageHashes.clear();
            log.debug("[DUP-GUARD] cache cleared");
        }
        return dup;
    }

    public void markProcessed(String hash) {
        processedMessageHashes.add(hash);
    }
}
