package com.example.demo.eth20.service.api;

import java.util.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import com.example.demo.eth20.node.EthereumNodeRegistry;
import org.springframework.web.client.RestClientException;

/** Admin API business logic. Blockchain operations retain their existing demo behavior. */
@Service
@RequiredArgsConstructor
public class AdminApiService {
    private final Logger log;
    private final EthereumNodeRegistry nodes;

    public enum NodeQuery { BALANCE, SYNC, IDENTITY, VALIDATORS, PEERS }

    public Map<String, Object> queryNodes(String nodeId, NodeQuery query, String filter) {
        // Validate request input before handling individual upstream failures.
        if (query == NodeQuery.BALANCE && filter != null && !filter.matches("0x[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException("A 20-byte Ethereum address is required");
        }
        if (query == NodeQuery.VALIDATORS && filter != null
                && !filter.matches("(?:[0-9]+|0x[0-9a-fA-F]{96})")) {
            throw new IllegalArgumentException("Validator ID must be an index or a 48-byte public key");
        }
        if (nodeId == null || nodeId.isBlank()) {
            return Map.of("nodes", nodes.clients().keySet().stream().sorted()
                    .map(id -> queryNodes(id, query, filter)).toList());
        }
        var client = nodes.client(nodeId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeId", nodeId);
        try {
            result.putAll(switch (query) {
                case BALANCE -> client.balances(filter);
                case SYNC -> client.syncStatus();
                case IDENTITY -> client.identity();
                case VALIDATORS -> client.validators(filter);
                case PEERS -> client.peers();
            });
            log.info("Ethereum node query completed: nodeId={}, query={}", nodeId, query);
        } catch (RestClientException | IllegalStateException ex) {
            result.put("status", "UNAVAILABLE");
            result.put("reason", ex.getClass().getSimpleName());
            log.warn("Ethereum node query failed: nodeId={}, query={}, reason={}",
                    nodeId, query, ex.getClass().getSimpleName());
        }
        return result;
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
                    status.get("nodeId"), status.get("status"), status.get("latestBlock"), status.get("peers"));
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

    public String approveWithdrawal(String txHash, boolean isApproved) {
        log.info("Withdrawal approval demo: txHash={}, approved={}", txHash, isApproved);
        // 註解：若通過則呼叫 Smart Contract 的 multi-sig 多簽功能
        return isApproved ? "交易 " + txHash + " 已核准並送出區塊鏈" : "交易已駁回";
    }

    public String emergencyStop() {
        log.warn("Emergency stop demo requested; no transaction controls are configured");
        // 註解：此操作會被 AOP 紀錄為最高警示 Level
        return "CRITICAL: 系統所有 ETH 交易已暫停！";
    }

    public Map<String, Object> updateGasMultiplier(Map<String, Double> config) {
        Map<String, Object> res = new HashMap<>();
        res.getOrDefault("oldValue", 1.1);
        res.put("newValue", config.get("multiplier"));
        res.put("status", "Applied");
        return res;
    }

    public String triggerError() {
        // 直接拋出通用異常，Spring 會交給 @RestControllerAdvice
        throw new RuntimeException("連線至 ETH 節點時發生不可預期的崩潰！");
    }

    public String syncBlacklist() {
        return "Blacklist sync completed with Ethereum Smart Contract.";
    }

    public boolean checkKeyStore() {
        // 註解：確保後端有權限讀取部署於 AWS 的錢包私鑰
        return true;
    }
}
