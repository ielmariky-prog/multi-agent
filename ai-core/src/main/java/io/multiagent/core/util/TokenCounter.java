
package io.multiagent.core.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TokenCounter {

    private static final Logger log = LoggerFactory.getLogger(TokenCounter.class);

    private final Encoding encoding;

    public TokenCounter() {
        // GPT-4 / GPT-5 series use CL100K_BASE
        this.encoding = Encodings.newDefaultEncodingRegistry()
                .getEncoding(EncodingType.CL100K_BASE);

        log.info("🔢 TokenCounter initialized with CL100K_BASE encoding.");
    }

    /**
     * Compte les tokens dans un texte.
     */
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return encoding.countTokens(text);
    }

    /**
     * Report complet pour logs.
     */
    public void logTokens(String label, String text) {
        int tokens = countTokens(text);
        log.info("🧮 [{}] Tokens = {}", label, tokens);
    }
}