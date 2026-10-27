package com.example.demo.eth20.controller;

import com.example.demo.eth20.service.api.EthApiService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 *ETH core development interface (Core Web3 Service)
 *Handle Web3j connections, smart contract interactions, and key management
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("eth/core/api")
@Tag(name = "ETH 核心開發介面", description = "包含智能合約部署、錢包管理與節點通訊核心功能")
public class Eth_APIController {

    private final EthApiService ethService;

    @Operation(summary = "1. 錢包私鑰認證", description = "驗證私鑰格式並檢查其在 AWS Secrets Manager 中的安全性")
    @PostMapping("/wallet/auth")
    public Map<String, Object> authPrivateKey(@RequestBody Map<String, String> request) {
        return ethService.authPrivateKey(request);
    }

    // 2. 智能合約部署 (Contract Deployment)
    @Operation(summary = "2. 部署智能合約", description = "將編譯好的 Solidity Bytecode 部署至指定的 ETH 網絡")
    @PostMapping("/contract/deploy")
    public Map<String, String> deployContract(@RequestParam String contractName) {
        return ethService.deployContract(contractName);
    }

    // 3. 節點溝通與同步 (Node Sync Status)
    @Operation(summary = "3. 節點溝通狀態", description = "取得當前後端連結之 ETH 節點的最新區塊與同步進度")
    @GetMapping("/node/info")
    public Map<String, Object> getNodeInfo() {
        return ethService.getNodeInfo();
    }

    // 4. 離線簽署與發送交易 (Offline Signing)
    @Operation(summary = "4. 簽署並發送交易", description = "使用私鑰在本地簽署原始交易 (Raw Transaction) 並廣播至網路")
    @PostMapping("/transaction/send")
    public String sendRawTransaction(@RequestBody Map<String, String> txData) {
        return ethService.sendRawTransaction(txData);
    }
}