package com.main.accord.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final String ALGORITHM  = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH  = 12;   // 96 bits — GCM standard
    private static final int    TAG_LENGTH = 128;  // bits

    @Value("${accord.encryption.key}")
    private String rawKey;  // must be exactly 32 characters (AES-256)

    public String encrypt(String plaintext) {
        try {
            byte[] iv  = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(rawKey.getBytes(), "AES"),
                    new GCMParameterSpec(TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            // Prefix IV to ciphertext so we can extract it on decrypt
            byte[] combined = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv,         0, combined, 0,         IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed.", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined    = Base64.getDecoder().decode(encoded);
            byte[] iv          = new byte[IV_LENGTH];
            byte[] ciphertext  = new byte[combined.length - IV_LENGTH];

            System.arraycopy(combined, 0,         iv,         0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(rawKey.getBytes(), "AES"),
                    new GCMParameterSpec(TAG_LENGTH, iv));

            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed.", e);
        }
    }
}