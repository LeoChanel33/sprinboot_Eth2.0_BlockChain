package com.example.demo.eth20.service.api;

import java.util.*;
import org.springframework.stereotype.Service;

/** Eth API business logic. Blockchain operations retain their existing demo behavior. */
@Service
public class EthApiService {

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
        // 模擬 500 錯誤：如果 Web3j 連不到 AWS 上的 Geth 節點
        boolean isNodeDown = true; // 模擬故障
        if (isNodeDown) {
            throw new RuntimeException("無法連結至 ETH 遠端節點 (JSON-RPC Timeout)");
        }

        Map<String, Object> info = new HashMap<>();
        info.put("clientVersion", "Geth/v1.13.0/linux-amd64");
        info.put("networkId", 1); // Mainnet
        return info;
    }

    public String sendRawTransaction(Map<String, String> txData) {
        // 註解：Web3j TransactionManager 邏輯
        return "Transaction hash: 0x9876543210fedcba9876543210fedcba98765432";
    }
}
