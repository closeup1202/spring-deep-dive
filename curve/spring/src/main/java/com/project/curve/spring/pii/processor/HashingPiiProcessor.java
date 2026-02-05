package com.project.curve.spring.pii.processor;

import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.crypto.PiiCryptoProvider;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * HASH strategy processor.
 * Performs SHA-256 hashing, which is irreversible.
 */
@Component
@RequiredArgsConstructor
public class HashingPiiProcessor implements PiiProcessor {

    private final PiiCryptoProvider cryptoProvider;

    @Override
    public String process(String value, PiiField piiField) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String hashed = cryptoProvider.hash(value);
        return "HASH(" + hashed + ")";
    }

    @Override
    public PiiStrategy supportedStrategy() {
        return PiiStrategy.HASH;
    }
}
