package com.project.curve.spring.pii.crypto;

import com.project.curve.spring.exception.PiiCryptoException;
import lombok.Getter;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default PII encryption provider.
 * <p>
 * Supports AES-256-GCM encryption and SHA-256 hashing.
 * <p>
 * <b>Security Notes:</b>
 * <ul>
 *   <li>To use encryption features, {@code curve.pii.crypto.default-key} must be configured.</li>
 *   <li>An exception is thrown if {@link #encrypt} is called without a key configured.</li>
 *   <li>Hashing can be used without a key, but configuring a salt is recommended.</li>
 * </ul>
 */
public class DefaultPiiCryptoProvider implements PiiCryptoProvider {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey defaultKey;
    private final Map<String, SecretKey> keyStore;
    private final String salt;
    private final SecureRandom secureRandom;
    @Getter
    private final boolean encryptionEnabled;

    /**
     * Creates a DefaultPiiCryptoProvider.
     *
     * @param defaultKeyBase64 Base64-encoded AES-256 encryption key (nullable, disables encryption if null)
     * @param salt             Salt for hashing (nullable)
     */
    public DefaultPiiCryptoProvider(String defaultKeyBase64, String salt) {
        this.encryptionEnabled = defaultKeyBase64 != null && !defaultKeyBase64.isBlank();
        this.defaultKey = encryptionEnabled ? createKey(defaultKeyBase64) : null;
        this.keyStore = new ConcurrentHashMap<>();
        this.salt = salt != null ? salt : "";
        this.secureRandom = new SecureRandom();
    }

    /**
     * Registers an additional key.
     *
     * @param alias     Key alias
     * @param keyBase64 Base64-encoded AES-256 key
     * @throws IllegalArgumentException if the key is null or blank
     */
    public void registerKey(String alias, String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalArgumentException("Encryption key cannot be null or blank. alias: " + alias);
        }
        keyStore.put(alias, createKey(keyBase64));
    }

    private SecretKey createKey(String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalArgumentException(
                    "PII encryption key is not configured. " +
                    "curve.pii.crypto.default-key configuration is required. " +
                    "Using PII_ENCRYPTION_KEY environment variable is recommended."
            );
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(keyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 format for encryption key.", e);
        }

        // Adjust key length to 32 bytes (256 bits)
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        } else if (keyBytes.length > 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts a value using AES-256-GCM.
     *
     * @param value    Original value to encrypt
     * @param keyAlias Key alias to use (uses default key if null)
     * @return Base64-encoded ciphertext (includes IV)
     * @throws PiiCryptoException if encryption key is not configured or encryption fails
     */
    @Override
    public String encrypt(String value, String keyAlias) {
        if (value == null) return null;

        if (!encryptionEnabled) {
            throw new PiiCryptoException(
                    "PII encryption is disabled. " +
                    "Set curve.pii.crypto.default-key. " +
                    "Environment variable: PII_ENCRYPTION_KEY"
            );
        }

        try {
            SecretKey key = resolveKey(keyAlias);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);

            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            byte[] encryptedBytes = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext and return
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (PiiCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new PiiCryptoException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts a value encrypted with AES-256-GCM.
     *
     * @param encryptedValue Base64-encoded ciphertext (includes IV)
     * @param keyAlias       Key alias to use (uses default key if null)
     * @return Decrypted original value
     * @throws PiiCryptoException if encryption key is not configured or decryption fails
     */
    @Override
    public String decrypt(String encryptedValue, String keyAlias) {
        if (encryptedValue == null) return null;

        if (!encryptionEnabled) {
            throw new PiiCryptoException(
                    "PII encryption is disabled. " +
                    "Set curve.pii.crypto.default-key."
            );
        }

        try {
            SecretKey key = resolveKey(keyAlias);
            byte[] combined = Base64.getDecoder().decode(encryptedValue);

            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] encryptedBytes = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (PiiCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new PiiCryptoException("Decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Hashes a value using SHA-256.
     * <p>
     * Hashing can be used without an encryption key, but configuring a salt is recommended.
     *
     * @param value Original value to hash
     * @return Base64-encoded hash value
     * @throws PiiCryptoException if hashing fails
     */
    @Override
    public String hash(String value) {
        if (value == null) return null;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String saltedValue = salt + value;
            byte[] hashBytes = digest.digest(saltedValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            throw new PiiCryptoException("Hashing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves the key to use by key alias.
     *
     * @param keyAlias Key alias (uses default key if null or empty)
     * @return The corresponding key or default key
     */
    private SecretKey resolveKey(String keyAlias) {
        if (keyAlias == null || keyAlias.isEmpty()) {
            return defaultKey;
        }
        return keyStore.getOrDefault(keyAlias, defaultKey);
    }
}
