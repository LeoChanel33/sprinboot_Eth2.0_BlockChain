package com.example.demo.eth20.service.jpa;

import com.example.demo.eth20.entity.UserAccount;
import com.example.demo.eth20.repository.UserAccountRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class UserJpaService {
    private final UserAccountRepository repository;

    public UserAccount register(String username, String email) {
        username = InputChecks.text(username, "username", 64).toLowerCase(Locale.ROOT);
        email = InputChecks.text(email, "email", 254).toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Invalid email");
        }
        if (repository.existsByUsername(username) || repository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already registered");
        }
        return repository.save(new UserAccount(username, email));
    }

    @Transactional(readOnly = true)
    public UserAccount get(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Invalid user id");
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
