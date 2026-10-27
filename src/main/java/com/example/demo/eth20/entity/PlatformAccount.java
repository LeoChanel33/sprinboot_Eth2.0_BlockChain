package com.example.demo.eth20.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "platform_accounts", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "platform"}),
    @UniqueConstraint(columnNames = {"platform", "external_account_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;
    @Column(nullable = false, length = 64)
    private String platform;
    @Column(name = "external_account_id", nullable = false, length = 255)
    private String externalAccountId;

    public PlatformAccount(UserAccount user, String platform, String externalAccountId) {
        this.user = user;
        this.platform = platform;
        this.externalAccountId = externalAccountId;
    }
}
