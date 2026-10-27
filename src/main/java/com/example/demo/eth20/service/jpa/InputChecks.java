package com.example.demo.eth20.service.jpa;

final class InputChecks {
    private InputChecks() {}

    static String text(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " is required and must not exceed " + maxLength + " characters");
        }
        return value.trim();
    }
}
