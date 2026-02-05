package com.project.curve.spring.pii.crypto;

import com.project.curve.spring.exception.PiiCryptoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DefaultPiiCryptoProvider Test")
class DefaultPiiCryptoProviderTest {

    private DefaultPiiCryptoProvider cryptoProvider;
    private String salt;

    @BeforeEach
    void setUp() {
        // Generate 32-byte AES key (Base64 encoded)
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            keyBytes[i] = (byte) i;
        }
        String defaultKeyBase64 = Base64.getEncoder().encodeToString(keyBytes);
        salt = "test-salt";
        cryptoProvider = new DefaultPiiCryptoProvider(defaultKeyBase64, salt);
    }

    @Test
    @DisplayName("Encrypts a string and returns a different value from the original")
    void encrypt_shouldReturnDifferentValue() {
        // Given
        String plainText = "sensitive-data";

        // When
        String encrypted = cryptoProvider.encrypt(plainText, null);

        // Then
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(plainText);
        assertThat(encrypted).isBase64();
    }

    @Test
    @DisplayName("Encrypts the same string multiple times and returns different values each time (due to random IV)")
    void encrypt_samePlainText_shouldReturnDifferentValues() {
        // Given
        String plainText = "sensitive-data";

        // When
        String encrypted1 = cryptoProvider.encrypt(plainText, null);
        String encrypted2 = cryptoProvider.encrypt(plainText, null);

        // Then
        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    @DisplayName("Decrypts an encrypted string and returns the original value")
    void decrypt_shouldReturnOriginalValue() {
        // Given
        String plainText = "sensitive-data";
        String encrypted = cryptoProvider.encrypt(plainText, null);

        // When
        String decrypted = cryptoProvider.decrypt(encrypted, null);

        // Then
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("Encrypts null and returns null")
    void encrypt_withNull_shouldReturnNull() {
        // When
        String encrypted = cryptoProvider.encrypt(null, null);

        // Then
        assertThat(encrypted).isNull();
    }

    @Test
    @DisplayName("Decrypts null and returns null")
    void decrypt_withNull_shouldReturnNull() {
        // When
        String decrypted = cryptoProvider.decrypt(null, null);

        // Then
        assertThat(decrypted).isNull();
    }

    @Test
    @DisplayName("Decrypts invalid ciphertext and throws an exception")
    void decrypt_withInvalidCiphertext_shouldThrowException() {
        // Given
        String invalidCiphertext = "invalid-base64-!!!";

        // When & Then
        assertThatThrownBy(() -> cryptoProvider.decrypt(invalidCiphertext, null))
                .isInstanceOf(PiiCryptoException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    @DisplayName("Hashes the same value and always returns the same result")
    void hash_sameValue_shouldReturnSameHash() {
        // Given
        String value = "test@example.com";

        // When
        String hash1 = cryptoProvider.hash(value);
        String hash2 = cryptoProvider.hash(value);

        // Then
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(value);
    }

    @Test
    @DisplayName("Hashes different values and returns different results")
    void hash_differentValues_shouldReturnDifferentHashes() {
        // Given
        String value1 = "test1@example.com";
        String value2 = "test2@example.com";

        // When
        String hash1 = cryptoProvider.hash(value1);
        String hash2 = cryptoProvider.hash(value2);

        // Then
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Hashes null and returns null")
    void hash_withNull_shouldReturnNull() {
        // When
        String hash = cryptoProvider.hash(null);

        // Then
        assertThat(hash).isNull();
    }

    @Test
    @DisplayName("Registers an additional key and allows encryption with it")
    void registerKey_shouldAllowEncryptionWithCustomKey() {
        // Given
        byte[] customKeyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            customKeyBytes[i] = (byte) (i + 100);
        }
        String customKeyBase64 = Base64.getEncoder().encodeToString(customKeyBytes);
        cryptoProvider.registerKey("custom-key", customKeyBase64);
        String plainText = "sensitive-data";

        // When
        String encryptedWithCustomKey = cryptoProvider.encrypt(plainText, "custom-key");
        String decryptedWithCustomKey = cryptoProvider.decrypt(encryptedWithCustomKey, "custom-key");

        // Then
        assertThat(decryptedWithCustomKey).isEqualTo(plainText);
    }

    @Test
    @DisplayName("Fails to decrypt data encrypted with a different key")
    void decrypt_withDifferentKey_shouldFail() {
        // Given
        byte[] customKeyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            customKeyBytes[i] = (byte) (i + 100);
        }
        String customKeyBase64 = Base64.getEncoder().encodeToString(customKeyBytes);
        cryptoProvider.registerKey("custom-key", customKeyBase64);
        String plainText = "sensitive-data";
        String encryptedWithDefaultKey = cryptoProvider.encrypt(plainText, null);

        // When & Then: Attempt to decrypt with a different key
        assertThatThrownBy(() -> cryptoProvider.decrypt(encryptedWithDefaultKey, "custom-key"))
                .isInstanceOf(PiiCryptoException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    @DisplayName("Encrypts and decrypts an empty string successfully")
    void encrypt_decrypt_emptyString_shouldWork() {
        // Given
        String emptyString = "";

        // When
        String encrypted = cryptoProvider.encrypt(emptyString, null);
        String decrypted = cryptoProvider.decrypt(encrypted, null);

        // Then
        assertThat(decrypted).isEqualTo(emptyString);
    }

    @Test
    @DisplayName("Encrypts and decrypts a long string successfully")
    void encrypt_decrypt_longString_shouldWork() {
        // Given
        String longString = "a".repeat(10000);

        // When
        String encrypted = cryptoProvider.encrypt(longString, null);
        String decrypted = cryptoProvider.decrypt(encrypted, null);

        // Then
        assertThat(decrypted).isEqualTo(longString);
    }

    @Test
    @DisplayName("Encrypts and decrypts a string containing special characters successfully")
    void encrypt_decrypt_specialCharacters_shouldWork() {
        // Given
        String specialString = "!@#$%^&*()_+-=[]{}|;':\",./<>?\\`~한글テスト中文";

        // When
        String encrypted = cryptoProvider.encrypt(specialString, null);
        String decrypted = cryptoProvider.decrypt(encrypted, null);

        // Then
        assertThat(decrypted).isEqualTo(specialString);
    }

    @Test
    @DisplayName("Enables encryption when a key is set")
    void isEncryptionEnabled_withKey_shouldReturnTrue() {
        // Given & When & Then
        assertThat(cryptoProvider.isEncryptionEnabled()).isTrue();
    }

    @Test
    @DisplayName("Disables encryption when the key is null")
    void isEncryptionEnabled_withNullKey_shouldReturnFalse() {
        // Given
        DefaultPiiCryptoProvider providerWithoutKey = new DefaultPiiCryptoProvider(null, salt);

        // When & Then
        assertThat(providerWithoutKey.isEncryptionEnabled()).isFalse();
    }

    @Test
    @DisplayName("Disables encryption when the key is an empty string")
    void isEncryptionEnabled_withEmptyKey_shouldReturnFalse() {
        // Given
        DefaultPiiCryptoProvider providerWithEmptyKey = new DefaultPiiCryptoProvider("", salt);

        // When & Then
        assertThat(providerWithEmptyKey.isEncryptionEnabled()).isFalse();
    }

    @Test
    @DisplayName("Disables encryption when the key contains only whitespace")
    void isEncryptionEnabled_withBlankKey_shouldReturnFalse() {
        // Given
        DefaultPiiCryptoProvider providerWithBlankKey = new DefaultPiiCryptoProvider("   ", salt);

        // When & Then
        assertThat(providerWithBlankKey.isEncryptionEnabled()).isFalse();
    }

    @Test
    @DisplayName("Throws an exception when encrypt is called with encryption disabled")
    void encrypt_withoutKey_shouldThrowException() {
        // Given
        DefaultPiiCryptoProvider providerWithoutKey = new DefaultPiiCryptoProvider(null, salt);
        String plainText = "sensitive-data";

        // When & Then
        assertThatThrownBy(() -> providerWithoutKey.encrypt(plainText, null))
                .isInstanceOf(PiiCryptoException.class)
                .hasMessageContaining("PII encryption is disabled")
                .hasMessageContaining("curve.pii.crypto.default-key");
    }

    @Test
    @DisplayName("Throws an exception when decrypt is called with encryption disabled")
    void decrypt_withoutKey_shouldThrowException() {
        // Given
        DefaultPiiCryptoProvider providerWithoutKey = new DefaultPiiCryptoProvider(null, salt);
        String encrypted = cryptoProvider.encrypt("test", null);

        // When & Then
        assertThatThrownBy(() -> providerWithoutKey.decrypt(encrypted, null))
                .isInstanceOf(PiiCryptoException.class)
                .hasMessageContaining("PII encryption is disabled");
    }

    @Test
    @DisplayName("Allows hashing even when encryption is disabled")
    void hash_withoutKey_shouldWork() {
        // Given
        DefaultPiiCryptoProvider providerWithoutKey = new DefaultPiiCryptoProvider(null, salt);
        String value = "test@example.com";

        // When
        String hash = providerWithoutKey.hash(value);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).isNotEqualTo(value);
    }

    @Test
    @DisplayName("Throws an exception when initialized with an invalid Base64 format key")
    void constructor_withInvalidBase64Key_shouldThrowException() {
        // Given
        String invalidBase64Key = "not-valid-base64!!!";

        // When & Then
        assertThatThrownBy(() -> new DefaultPiiCryptoProvider(invalidBase64Key, salt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Base64 format");
    }

    @Test
    @DisplayName("Throws an exception when registerKey is called with an empty key")
    void registerKey_withEmptyKey_shouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> cryptoProvider.registerKey("alias", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or blank");
    }

    @Test
    @DisplayName("Throws an exception when registerKey is called with a null key")
    void registerKey_withNullKey_shouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> cryptoProvider.registerKey("alias", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or blank");
    }
}
