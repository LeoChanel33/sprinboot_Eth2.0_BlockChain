package com.example.demo.eth20.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "eth_client_detail")
@Getter @Setter @NoArgsConstructor
public class EthClientDetail {
    @Id @Column(name = "hash_key", nullable = false) private String hashKey;

    @Column(name = "cash")
    private Double cash;

    @Column(name = "create_date")
    private LocalDate createDate;

    @Column(name = "modify_date")
    private LocalDate modifyDate;

    @Column(name = "agreed_account")
    private String agreedAccount;

}