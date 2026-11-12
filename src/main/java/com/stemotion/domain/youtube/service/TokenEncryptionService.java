package com.stemotion.domain.youtube.service;

import com.stemotion.domain.youtube.config.YoutubeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts and decrypts OAuth refresh tokens using AES-256-GCM.
 * Tokens are never logged and never exposed in API responses.
 * Derives distinct keys for AES encryption and OAuth state HMAC from YOUTUBE_TOKEN_KEY.
 */
@Service
@RequiredArgsConstructor
public class TokenEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private static final byte[] AES_DERIVATION_LABEL = "youtube-aes-token-encryption-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HMAC_DERIVATION_LABEL = "youtube-oauth-state-hmac-v1".getBytes(StandardCharsets.UTF_8);

    private final YoutubeProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    private byte[] getMasterKeyBytes() {
        String key = properties.getTokenKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("YOUTUBE_TOKEN_KEY is not configured");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(key);
            if (decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {}
        try {
            return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash master key", e);
        }
    }

    public SecretKey getDerivedAesKey() {
        byte[] master = getMasterKeyBytes();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(master, HMAC_ALGORITHM));
            byte[] derivedKey = mac.doFinal(AES_DERIVATION_LABEL);
            return new SecretKeySpec(derivedKey, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES encryption key", e);
        }
    }

    public byte[] getDerivedHmacKey() {
        byte[] master = getMasterKeyBytes();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(master, HMAC_ALGORITHM));
            return mac.doFinal(HMAC_DERIVATION_LABEL);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive HMAC state key", e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, getDerivedAesKey(), parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Token encryption failed", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        byte[] decoded = Base64.getDecoder().decode(ciphertext);
        if (decoded.length < IV_LENGTH_BYTES + 16) {
            throw new IllegalArgumentException("Invalid encrypted token format");
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
        byte[] iv = new byte[IV_LENGTH_BYTES];
        byteBuffer.get(iv);

        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);

        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);

        // 1. Try with newly derived AES key
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getDerivedAesKey(), parameterSpec);
            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception primaryEx) {
            // 2. Fallback to legacy raw key if exists (backward compatibility with tokens encrypted before key derivation)
            try {
                SecretKey legacyKey = new SecretKeySpec(getMasterKeyBytes(), "AES");
                Cipher legacyCipher = Cipher.getInstance(ALGORITHM);
                legacyCipher.init(Cipher.DECRYPT_MODE, legacyKey, parameterSpec);
                byte[] plainTextBytes = legacyCipher.doFinal(cipherText);
                return new String(plainTextBytes, StandardCharsets.UTF_8);
            } catch (Exception fallbackEx) {
                throw new IllegalStateException("Token decryption failed", primaryEx);
            }
        }
    }
}
