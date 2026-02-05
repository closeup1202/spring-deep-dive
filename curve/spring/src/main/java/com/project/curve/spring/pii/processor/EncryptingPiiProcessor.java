package com.project.curve.spring.pii.processor;

import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.crypto.PiiCryptoProvider;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ENCRYPT strategy processor.
 * Performs AES encryption, which is reversible.
 */
@Component
@RequiredArgsConstructor
public class EncryptingPiiProcessor implements PiiProcessor {

    private final PiiCryptoProvider cryptoProvider;

    @Override
    public String process(String value, PiiField piiField) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String keyAlias = piiField.encryptKey().isEmpty() ? null : piiField.encryptKey();
        String encrypted = cryptoProvider.encrypt(value, keyAlias);
        return "ENC(" + encrypted + ")";
    }

    @Override
    public PiiStrategy supportedStrategy() {
        return PiiStrategy.ENCRYPT;
    }
}
