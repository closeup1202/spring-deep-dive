package com.project.curve.spring.pii.processor;

import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.crypto.DefaultPiiCryptoProvider;
import com.project.curve.spring.pii.crypto.PiiCryptoProvider;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EncryptingPiiProcessorTest {

    private EncryptingPiiProcessor processor;
    private PiiCryptoProvider cryptoProvider;

    @BeforeEach
    void setUp() {
        // Generate 32-byte AES key
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            keyBytes[i] = (byte) i;
        }
        String keyBase64 = Base64.getEncoder().encodeToString(keyBytes);
        cryptoProvider = new DefaultPiiCryptoProvider(keyBase64, "test-salt");
        processor = new EncryptingPiiProcessor(cryptoProvider);
    }

    @Test
    @DisplayName("Supported strategy should be ENCRYPT")
    void supportedStrategy_shouldBeEncrypt() {
        // When
        PiiStrategy strategy = processor.supportedStrategy();

        // Then
        assertThat(strategy).isEqualTo(PiiStrategy.ENCRYPT);
    }

    @Test
    @DisplayName("Encrypted string should be wrapped with ENC() prefix")
    void process_shouldWrapWithEncPrefix() {
        // Given
        String value = "sensitive-data";
        PiiField piiField = createPiiField("");

        // When
        String encrypted = processor.process(value, piiField);

        // Then
        assertThat(encrypted).startsWith("ENC(");
        assertThat(encrypted).endsWith(")");
        assertThat(encrypted).isNotEqualTo(value);
    }

    @Test
    @DisplayName("Encrypted value should be different from original")
    void process_shouldReturnDifferentValue() {
        // Given
        String value = "sensitive-data";
        PiiField piiField = createPiiField("");

        // When
        String encrypted = processor.process(value, piiField);

        // Then
        assertThat(encrypted).isNotEqualTo(value);
        assertThat(encrypted).isNotEqualTo("ENC(" + value + ")");
    }

    @Test
    @DisplayName("Encrypting null should return null")
    void process_null_shouldReturnNull() {
        // Given
        PiiField piiField = createPiiField("");

        // When
        String encrypted = processor.process(null, piiField);

        // Then
        assertThat(encrypted).isNull();
    }

    @Test
    @DisplayName("Encrypting empty string should return empty string")
    void process_emptyString_shouldReturnEmpty() {
        // Given
        PiiField piiField = createPiiField("");

        // When
        String encrypted = processor.process("", piiField);

        // Then
        assertThat(encrypted).isEmpty();
    }

    @Test
    @DisplayName("Encrypting same value multiple times should return different results")
    void process_sameValue_shouldReturnDifferentResults() {
        // Given
        String value = "sensitive-data";
        PiiField piiField = createPiiField("");

        // When
        String encrypted1 = processor.process(value, piiField);
        String encrypted2 = processor.process(value, piiField);

        // Then
        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    @DisplayName("Should use custom key if encryptKey is specified")
    void process_withCustomKey_shouldUseCustomKey() {
        // Given
        String value = "sensitive-data";
        String customKeyAlias = "custom-key";
        PiiField piiField = createPiiField(customKeyAlias);

        // When
        String encrypted = processor.process(value, piiField);

        // Then
        assertThat(encrypted).startsWith("ENC(");
        assertThat(encrypted).endsWith(")");
    }

    @Test
    @DisplayName("Decrypted value should match original")
    void process_encrypt_decrypt_roundTrip() {
        // Given
        String value = "sensitive-data";
        PiiField piiField = createPiiField("");

        // When
        String encrypted = processor.process(value, piiField);
        String encryptedValue = encrypted.substring(4, encrypted.length() - 1); // Remove "ENC("
        String decrypted = cryptoProvider.decrypt(encryptedValue, null);

        // Then
        assertThat(decrypted).isEqualTo(value);
    }

    private PiiField createPiiField(String encryptKey) {
        PiiField piiField = mock(PiiField.class);
        when(piiField.type()).thenReturn(PiiType.CUSTOM);
        when(piiField.level()).thenReturn(MaskingLevel.STRONG);
        when(piiField.strategy()).thenReturn(PiiStrategy.ENCRYPT);
        when(piiField.encryptKey()).thenReturn(encryptKey);
        return piiField;
    }
}
