package com.example.demo.eth20.dto;

import com.example.demo.eth20.entity.UserAccount;

public record UserView(Long id, String username, String email) {
    public static UserView from(UserAccount user) {
        return new UserView(user.getId(), user.getUsername(), user.getEmail());
    }
}
