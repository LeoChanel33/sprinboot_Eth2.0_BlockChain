package com.example.demo.eth20.service.api;

import java.util.*;
import org.springframework.stereotype.Service;

/** Admin API business logic. Blockchain operations retain their existing demo behavior. */
@Service
public class AdminApiService {

    public Map<String, Object> checkNodeStatus(String nodeId) {
        Map<String, Object> status = new HashMap<>();
        status.put("nodeId", nodeId);
        status.put("isSynced", true);
        status.put("peers", 42);
        status.put("latestBlock", 19345678);
        return status;
    }

    public String approveWithdrawal(String txHash, boolean isApproved) {
        // 註解：若通過則呼叫 Smart Contract 的 multi-sig 多簽功能
        return isApproved ? "交易 " + txHash + " 已核准並送出區塊鏈" : "交易已駁回";
    }

    public String emergencyStop() {
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
