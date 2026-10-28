package com.example.demo.eth20.service.jpa;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PasswordHash {
    private PasswordHash() {}
    static String encode(String password) {
        if (password == null || password.isBlank() || password.length() > 1024)
            throw new IllegalArgumentException("password is required and must be at most 1024 characters");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 600000, 256);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return "pbkdf2-sha256$600000$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Password hashing unavailable", ex);
        } finally { spec.clearPassword(); }
    }
}