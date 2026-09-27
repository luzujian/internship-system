package com.internship.util;

import java.util.List;
import java.util.ArrayList;

/** Supported adapters, not claims about arbitrary provider model names. */
public final class AIModelCapabilities {
    private AIModelCapabilities() {}
    public static boolean dictation(String code) {
        return code != null && code.matches("qwen3\\.8-omni-flash(?:-\\d{4}-\\d{2}-\\d{2})?");
    }
    public static boolean realtime(String code) {
        return code != null && code.matches("qwen3-asr-flash-realtime(?:-\\d{4}-\\d{2}-\\d{2})?");
    }
    public static List<String> supported(String code) {
        var result = new ArrayList<String>();
        if (dictation(code)) return List.of("chat", "voice");
        if (realtime(code)) return List.of("voice");
        if (code != null && !code.matches(".*(?:asr|tts|realtime|embedding|rerank|whisper|audio|omni).*")
                && code.matches("(?:deepseek|qwen(?:\\d+(?:\\.\\d+)?)?|gpt|glm|claude|gemini|o[134])[-.].+")) result.add("chat");
        return result;
    }
}
