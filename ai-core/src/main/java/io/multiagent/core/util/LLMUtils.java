package io.multiagent.core.util;

public final class LLMUtils {

    private LLMUtils() {}

    // chatJson() retourne désormais un String directement
    // Cette méthode reste pour les services qui l'appellent encore
    public static String extractChatContent(String content) {
        return content == null ? "" : content;
    }
}
