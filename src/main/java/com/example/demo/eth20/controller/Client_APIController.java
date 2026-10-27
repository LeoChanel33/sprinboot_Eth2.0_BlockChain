package com.example.demo.eth20.controller;

import com.example.demo.eth20.dto.*;
import com.example.demo.eth20.entity.BlockchainNode;
import org.springframework.http.HttpStatus;

import com.example.demo.eth20.service.api.ClientApiService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ETH 2.0 client API (Client API) Integrate Swagger OpenAPI 3 specification
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("eth/client/api")
@Tag(name = "ETH 使用者操作介面", description = "包含錢包管理、轉帳、合約交互與節點認證功能")
public class Client_APIController {

    private final ClientApiService clientService;

    @Operation(summary = "註冊使用者，可同時建立外部平台帳號關聯")
    @PostMapping("/users/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView registerUser(
            @RequestBody Registration request) {
        return clientService.registerUser(request);
    }

    @GetMapping("/users/{userId}")
    public UserView getUser(@PathVariable Long userId) {
        return clientService.getUser(userId);
    }

    @PostMapping("/nodes")
    @ResponseStatus(HttpStatus.CREATED)
    public BlockchainNode registerNode(
            @RequestBody NodeRegistration request) {
        return clientService.registerNode(request);
    }

    @GetMapping("/nodes/{nodeId}")
    public BlockchainNode getRegisteredNode(@PathVariable String nodeId) {
        return clientService.getRegisteredNode(nodeId);
    }

    @PutMapping("/nodes/{nodeId}/status")
    public BlockchainNode updateNodeStatus(@PathVariable String nodeId,
            @RequestBody NodeStatusUpdate request) {
        return clientService.updateNodeStatus(nodeId, request);
    }

    @PostMapping("/users/{userId}/platforms")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformView linkPlatform(@PathVariable Long userId,
            @RequestBody PlatformLink request) {
        return clientService.linkPlatform(userId, request);
    }

    @GetMapping("/users/{userId}/platforms")
    public List<PlatformView> getPlatforms(@PathVariable Long userId) {
        return clientService.getPlatforms(userId);
    }

    @GetMapping("/redis")  //redis測試
    public String testRedis() {
        return clientService.testRedis();
    }

    @GetMapping("/kafka")  //kafka測試
    public String testKafka() {
        return clientService.testKafka();
    }

    @Operation(summary = "1. 查詢錢包餘額", description = "輸入 ETH 錢包地址，從節點獲取即時餘額 (ETH)")
    @GetMapping("/balance/{address}")
    public Map<String, Object> getBalance(
            @Parameter(description = "ETH 錢包地址 (0x...)", example = "0x742d35Cc6634C0532925a3b844Bc454e4438f44e") @PathVariable String address) {
        return clientService.getBalance(address);
    }

    @Operation(summary = "2. 發起 ETH 轉帳", description = "處理私鑰簽章並發送 Raw Transaction 到區塊鏈")
    @PostMapping("/transfer")
    public Map<String, String> transferEth(@RequestBody Map<String, String> request) {
        return clientService.transferEth(request);
    }

    @Operation(summary = "3. 智能合約提款", description = "呼叫部署在 K8s Pod 節點上的智慧合約 withdraw 函數")
    @PostMapping("/contract/withdraw")
    public String withdrawFromContract(@RequestParam String contractAddress) {
        return clientService.withdrawFromContract(contractAddress);
    }

    @Operation(summary = "4. 更改帳戶存取密碼", description = "更新資料庫中用於保護私鑰 KeyStore 的二次驗證密碼")
    @PutMapping("/account/password")
    public String updatePassword(@RequestBody Map<String, String> pwdData) {
        return clientService.updatePassword(pwdData);
    }

    @Operation(summary = "5. 地理位置與錢包認證", description = "全端整合：驗證使用者當前 IP 位置與 Web3 錢包簽章的一致性")
    @PostMapping("/verify/location")
    public Map<String, Object> verifyLocation(@RequestBody Map<String, Object> locationData) {
        return clientService.verifyLocation(locationData);
    }

    @Operation(summary = "6. 新增約定帳戶", description = "將常用的 ETH 地址新增至白名單（Whitelist），減少誤轉風險")
    @PostMapping("/account/trusted")
    public String addTrustedAccount(@RequestParam String alias, @RequestParam String address) {
        return clientService.addTrustedAccount(alias, address);
    }

    @Operation(summary = "7. 封鎖帳戶/黑名單", description = "在本地系統封鎖特定異常地址，並停止與其合約交互")
    @DeleteMapping("/account/block/{address}")
    public String blockAccount(@PathVariable String address) {
        return clientService.blockAccount(address);
    }

    @Operation(summary = "8. 智慧合約與節點資訊查詢", description = "獲取目前部署的合約版本與節點同步狀態")
    @GetMapping("/contract/info")
    public Map<String, String> getContractInfo() {
        return clientService.getContractInfo();
    }

    @Operation(summary = "9. Gas 費用預估", description = "動態計算當前區塊鏈網路擁塞情況下的交易手續費")
    @PostMapping("/gas/estimate")
    public Map<String, String> estimateGas() {
        return clientService.estimateGas();
    }
}
