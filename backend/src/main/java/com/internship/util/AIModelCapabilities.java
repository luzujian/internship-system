package com.internship.util;

import java.util.List;
import java.util.Locale;

/** Chat uses the OpenAI-compatible protocol; voice still requires a specific adapter. */
public final class AIModelCapabilities {
    private AIModelCapabilities() {}
    public static boolean dictation(String code) {
        return code != null && code.matches("qwen3\\.8-omni-flash(?:-\\d{4}-\\d{2}-\\d{2})?");
    }
    public static boolean realtime(String code) {
        return code != null && code.matches("qwen3-asr-flash-realtime(?:-\\d{4}-\\d{2}-\\d{2})?");
    }
    public static List<String> supported(String code) {
        if (code == null || code.isBlank()) return List.of();
        code = code.trim();
        if (dictation(code)) return List.of("chat", "voice");
        if (realtime(code)) return List.of("voice");
        String normalized = code.toLowerCase(Locale.ROOT);
        if (normalized.matches(".*(?:asr|tts|realtime|embed|rerank|whisper|text-to-speech|speech-to-text).*")
                || normalized.matches("(?:.*/)?(?:bge|e5|gte)[-_.].*")) return List.of();
        // Gateways may expose arbitrary aliases, deployment IDs or vendor/model paths.
        // Connectivity testing, not a provider-name whitelist, establishes availability.
        return List.of("chat");
    }
}
