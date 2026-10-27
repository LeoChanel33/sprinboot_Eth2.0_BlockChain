package com.example.demo.eth20.repository;

import com.example.demo.eth20.entity.PlatformAccount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccount, Long> {
    boolean existsByUserIdAndPlatform(Long userId, String platform);
    boolean existsByPlatformAndExternalAccountId(String platform, String externalAccountId);
    List<PlatformAccount> findByUserId(Long userId);
}
