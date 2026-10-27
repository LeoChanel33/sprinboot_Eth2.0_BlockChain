package com.example.demo.eth20.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "blockchain_nodes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlockchainNode {
    public enum Status { REGISTERED, SYNCING, ONLINE, OFFLINE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String nodeId;
    @Column(nullable = false, length = 2048)
    private String rpcUrl;
    @Column(nullable = false)
    private Long chainId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.REGISTERED;
    @Version
    private Long version;

    public BlockchainNode(String nodeId, String rpcUrl, Long chainId) {
        this.nodeId = nodeId;
        this.rpcUrl = rpcUrl;
        this.chainId = chainId;
    }

    public void updateStatus(Status status) {
        this.status = status;
    }
}
