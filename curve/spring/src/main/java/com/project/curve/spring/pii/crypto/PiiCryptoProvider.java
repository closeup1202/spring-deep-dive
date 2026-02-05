package com.project.curve.spring.pii.crypto;

/**
 * PII encryption/hashing provider interface.
 */
public interface PiiCryptoProvider {

    /**
     * Encrypts the value.
     *
     * @param value Original value
     * @param keyAlias Key alias (uses default key if null)
     * @return Encrypted value (Base64 encoded)
     */
    String encrypt(String value, String keyAlias);

    /**
     * Decrypts the encrypted value.
     *
     * @param encryptedValue Encrypted value (Base64 encoded)
     * @param keyAlias Key alias (uses default key if null)
     * @return Decrypted original value
     */
    String decrypt(String encryptedValue, String keyAlias);

    /**
     * Hashes the value (SHA-256).
     * Applies salt if configured.
     *
     * @param value Original value
     * @return Hashed value (Base64 encoded)
     */
    String hash(String value);
}
