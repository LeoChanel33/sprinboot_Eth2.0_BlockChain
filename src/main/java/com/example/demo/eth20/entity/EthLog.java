package com.example.demo.eth20.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "eth_log")
@Getter @Setter @NoArgsConstructor
public class EthLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;

    @Column(name = "register_log", columnDefinition = "TEXT")
    private String registerLog;

    @Column(name = "transfer_log", columnDefinition = "TEXT")
    private String transferLog;

    @Column(name = "approve_log", columnDefinition = "TEXT")
    private String approveLog;

    @Column(name = "lock_log", columnDefinition = "TEXT")
    private String lockLog;

    @Column(name = "unlock_log", columnDefinition = "TEXT")
    private String unlockLog;

    @Column(name = "modify_log", columnDefinition = "TEXT")
    private String modifyLog;

}