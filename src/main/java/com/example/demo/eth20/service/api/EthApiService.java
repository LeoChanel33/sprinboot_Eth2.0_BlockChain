package com.example.demo.eth20.service.api;

import java.util.*;
import org.springframework.stereotype.Service;
import com.example.demo.eth20.node.EthereumNodeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientException;

/** Eth API business logic. Blockchain operations retain their existing demo behavior. */
@Service
@Slf4j
@RequiredArgsConstructor
public class EthApiService {
    private final EthereumNodeRegistry nodes;

    public Map<String, Object> authPrivateKey(Map<String, String> request) {
        String pkey = request.get("privateKey");
        // 模擬驗證：如果私鑰長度不對，拋出 400 錯誤
        if (pkey == null || pkey.length() != 64) {
            throw new IllegalArgumentException("無效的私鑰長度，應為 64 位十六進位字串");
        }
        Map<String, Object> res = new HashMap<>();
        res.put("address", "0x742d35Cc6634C0532925a3b844Bc454e4438f44e");
        res.put("status", "Authenticated");
        return res;
    }

    public Map<String, String> deployContract(String contractName) {
        // 註解：這會呼叫 Web3j 的 deploy 方法
        Map<String, String> res = new HashMap<>();
        res.put("contractName", contractName);
        res.put("contractAddress", "0x1234567890abcdef1234567890abcdef12345678");
        res.put("transactionHash", "0xafeb...1234");
        return res;
    }

    public Map<String, Object> getNodeInfo() {
        return nodes.defaultClient().info();
    }

    public Map<String, Object> checkNodeStatus(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            List<Map<String, Object>> statuses = nodes.clients().keySet().stream()
                    .sorted().map(this::checkNodeStatus).toList();
            return Map.of("nodes", statuses);
        }
        var client = nodes.client(nodeId);
        try {
            Map<String, Object> status = client.status();
            log.info("Ethereum node status: nodeId={}, status={}, latestBlock={}, peers={}",
                    nodeId, status.get("status"), status.get("latestBlock"), status.get("peers"));
            return status;
        } catch (RestClientException ex) {
            log.warn("Ethereum node {} RPC unavailable ({})", nodeId, ex.getClass().getSimpleName());
            Map<String, Object> status = new HashMap<>();
            status.put("nodeId", nodeId);
            status.put("reachable", false);
            status.put("status", "UNAVAILABLE");
            status.put("isSynced", null);
            status.put("peers", null);
            status.put("latestBlock", null);
            return status;
        }
    }

    public String sendRawTransaction(Map<String, String> txData) {
        // 註解：Web3j TransactionManager 邏輯
        return "Transaction hash: 0x9876543210fedcba9876543210fedcba98765432";
    }
}
