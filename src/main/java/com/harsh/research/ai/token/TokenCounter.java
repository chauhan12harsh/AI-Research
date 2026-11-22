
package com.harsh.research.ai.token;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;

@Component
public class TokenCounter {

    private final Encoding encoding;

    public TokenCounter() {
        this.encoding = Encodings.newDefaultEncodingRegistry()
                .getEncoding(EncodingType.O200K_BASE);
    }

    public int count(String text) {
        return text == null || text.isEmpty()
                ? 0
                : encoding.countTokens(text);
    }
}
