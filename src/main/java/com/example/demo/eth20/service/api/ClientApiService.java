package com.example.demo.eth20.service.api;

import java.util.*;
import com.example.demo.eth20.dto.*;
import com.example.demo.eth20.entity.UserAccount;
import com.example.demo.eth20.service.jpa.UserJpaService;
import com.example.demo.eth20.service.jpa.NodeJpaService;
import com.example.demo.eth20.service.jpa.CrossPlatformJpaService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import lombok.extern.slf4j.Slf4j;
import com.example.demo.eth20.entity.BlockchainNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Client API business logic. Blockchain operations retain their existing demo behavior. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientApiService {
    private final UserJpaService users;
    private final NodeJpaService nodes;
    private final CrossPlatformJpaService platforms;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** Register and optionally link a platform in one transaction; any failure rolls back both. */
    @Transactional
    public UserView registerUser(Registration request) {
        if (request == null) throw new IllegalArgumentException("Registration is required");
        UserAccount user = users.register(request.username(), request.email());
        if (request.platform() != null) {
            platforms.link(user.getId(), request.platform().platform(), request.platform().externalAccountId());
        }
        return UserView.from(user);
    }

    @Transactional(readOnly = true)
    public UserView getUser(Long userId) {
        return UserView.from(users.get(userId));
    }

    @Transactional
    public BlockchainNode registerNode(NodeRegistration request) {
        if (request == null) throw new IllegalArgumentException("Node registration is required");
        return nodes.register(request.nodeId(), request.rpcUrl(), request.chainId());
    }

    @Transactional(readOnly = true)
    public BlockchainNode getRegisteredNode(String nodeId) {
        return nodes.get(nodeId);
    }

    @Transactional
    public BlockchainNode updateNodeStatus(String nodeId, NodeStatusUpdate request) {
        if (request == null) throw new IllegalArgumentException("Node status is required");
        return nodes.updateStatus(nodeId, request.status());
    }

    @Transactional
    public PlatformView linkPlatform(Long userId, PlatformLink request) {
        if (request == null) throw new IllegalArgumentException("Platform link is required");
        return PlatformView.from(platforms.link(userId, request.platform(), request.externalAccountId()));
    }

    @Transactional(readOnly = true)
    public List<PlatformView> getPlatforms(Long userId) {
        return platforms.list(userId).stream().map(PlatformView::from).toList();
    }

    public String testRedis() {
        StringBuilder result = new StringBuilder();

        // 2. 測試 Redis 連線邏輯
        try {
            Long count = redisTemplate.opsForValue().increment("leo-test-count");
            result.append("Redis 狀態：[連線成功] - 目前計數: " + count);
        } catch (Exception e) {
            result.append("Redis 狀態：[連線失敗] - 錯誤: " + e.getMessage());
        }

        return result.toString();
    }

    public String testKafka() {
        String mockData = "{'transaction': '0xabc123', 'amount': '1.5 ETH', 'status': 'PENDING'}";

        // 注意：這裡的 Topic 名稱要跟你的 @KafkaListener 一致
        kafkaTemplate.send("test-topic", mockData);

        return "Kafka 訊息已發送至 eth-events Topic！請檢查 Console 日誌。";
    }

    @KafkaListener(topics = "test-topic", groupId = "leo-group")
    public void listen(String message) {
        log.info("【Kafka 監聽成功】接收到 ETH 交易數據: {}", message);
        System.out.println("收到 Kafka 訊息並開始處理: " + message);
        // 如果是錯誤資訊可以用 log.error
        if (message.contains("ERROR")) {
            log.error("偵測到異常交易狀態！內容: {}", message);
        }
    }

    public Map<String, Object> getBalance(String address) {
        Map<String, Object> response = new HashMap<>();
        response.put("address", address);
        response.put("balance_eth", "1.25");
        response.put("status", "success");
        return response;
    }

    public Map<String, String> transferEth(Map<String, String> request) {
        Map<String, String> res = new HashMap<>();
        res.put("txHash", "0xabc123... (此為模擬 Hash)");
        res.put("message", "交易已提交至 ETH 2.0 節點，等待區塊確認");
        return res;
    }

    public String withdrawFromContract(String contractAddress) {
        return "智慧合約提款請求已發送至地址: " + contractAddress;
    }

    public String updatePassword(Map<String, String> pwdData) {
        return "安全性密碼更新成功";
    }

    public Map<String, Object> verifyLocation(Map<String, Object> locationData) {
        Map<String, Object> result = new HashMap<>();
        result.put("isVerified", true);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    public String addTrustedAccount(String alias, String address) {
        return "成功新增約定帳戶: " + alias + " (" + address + ")";
    }

    public String blockAccount(String address) {
        return "地址 " + address + " 已被系統封鎖";
    }

    public Map<String, String> getContractInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("network", "ETH 2.0 Testnet (Sepolia)");
        info.put("node_status", "Synced");
        return info;
    }

    public Map<String, String> estimateGas() {
        Map<String, String> gas = new HashMap<>();
        gas.put("low", "15 Gwei");
        gas.put("market", "25 Gwei");
        gas.put("fast", "40 Gwei");
        return gas;
    }
}
