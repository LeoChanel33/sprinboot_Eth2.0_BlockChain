package com.example.demo.eth20.service;

import com.example.demo.eth20.entity.PlatformAccount;
import com.example.demo.eth20.entity.UserAccount;
import com.example.demo.eth20.repository.PlatformAccountRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class CrossPlatformJpaService {
    private final PlatformAccountRepository repository;
    private final UserJpaService users;

    public PlatformAccount link(Long userId, String platform, String externalAccountId) {
        UserAccount user = users.get(userId);
        platform = InputChecks.text(platform, "platform", 64).toLowerCase(Locale.ROOT);
        externalAccountId = InputChecks.text(externalAccountId, "externalAccountId", 255);
        if (repository.existsByUserIdAndPlatform(userId, platform)
                || repository.existsByPlatformAndExternalAccountId(platform, externalAccountId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Platform account already linked");
        }
        return repository.save(new PlatformAccount(user, platform, externalAccountId));
    }

    @Transactional(readOnly = true)
    public List<PlatformAccount> list(Long userId) {
        users.get(userId);
        return repository.findByUserId(userId);
    }
}
