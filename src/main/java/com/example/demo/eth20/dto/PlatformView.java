package com.example.demo.eth20.dto;

import com.example.demo.eth20.entity.PlatformAccount;

public record PlatformView(Long id, Long userId, String platform, String externalAccountId) {
    public static PlatformView from(PlatformAccount account) {
        return new PlatformView(account.getId(), account.getUser().getId(),
                account.getPlatform(), account.getExternalAccountId());
    }
}
