package com.example.demo.eth20.repository;

import com.example.demo.eth20.entity.BlockchainNode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainNodeRepository extends JpaRepository<BlockchainNode, Long> {
    boolean existsByNodeId(String nodeId);
    Optional<BlockchainNode> findByNodeId(String nodeId);
}
