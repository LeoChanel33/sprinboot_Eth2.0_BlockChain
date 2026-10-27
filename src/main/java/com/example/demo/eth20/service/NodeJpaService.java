package com.example.demo.eth20.service;

import com.example.demo.eth20.entity.BlockchainNode;
import com.example.demo.eth20.repository.BlockchainNodeRepository;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class NodeJpaService {
    private final BlockchainNodeRepository repository;

    public BlockchainNode register(String nodeId, String rpcUrl, Long chainId) {
        nodeId = InputChecks.text(nodeId, "nodeId", 64);
        rpcUrl = InputChecks.text(rpcUrl, "rpcUrl", 2048);
        URI uri = URI.create(rpcUrl);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("rpcUrl must be an HTTP(S) URL without credentials");
        }
        if (chainId == null || chainId <= 0) throw new IllegalArgumentException("chainId must be positive");
        if (repository.existsByNodeId(nodeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Node already registered");
        }
        return repository.save(new BlockchainNode(nodeId, rpcUrl, chainId));
    }

    @Transactional(readOnly = true)
    public BlockchainNode get(String nodeId) {
        return repository.findByNodeId(InputChecks.text(nodeId, "nodeId", 64))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Node not found"));
    }

    public BlockchainNode updateStatus(String nodeId, BlockchainNode.Status status) {
        if (status == null) throw new IllegalArgumentException("status is required");
        BlockchainNode node = get(nodeId);
        node.updateStatus(status);
        return node;
    }
}
