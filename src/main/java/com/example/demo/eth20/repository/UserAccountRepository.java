package com.example.demo.eth20.repository;

import com.example.demo.eth20.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
