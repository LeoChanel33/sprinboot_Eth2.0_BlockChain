package com.example.demo.eth20.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "eth_admin")
@Getter @Setter @NoArgsConstructor
public class EthAdmin {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;

    @Column(name = "account", nullable = false, unique = true)
    private String account;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "level")
    private Integer level;

    @Column(name = "create_date")
    private LocalDate createDate;

    @Column(name = "create_account")
    private String createAccount;

    @Column(name = "mail")
    private String mail;

    @Column(name = "phone")
    private String phone;

    @Column(name = "address")
    private String address;

    @Column(name = "hash_key")
    private String hashKey;

}