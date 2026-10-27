package com.example.demo.eth20.controller;

import com.example.demo.eth20.service.api.AdminApiService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * ETH 2.0 系統管理端 (Admin API)
 * Responsible for node monitoring, access control management, and security auditing
 * Integrate Swagger OpenAPI 3 specification
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("eth/admin/api")
@Tag(name = "ETH 系統管理介面", description = "供管理員進行節點認證、權限審核與參數調優")
public class Admin_APIController {

    private final AdminApiService adminService;

    @Operation(summary = "1. 遠端節點狀態認證", description = "檢查 AWS EKS 上的 Geth 或 Erigon 節點連線是否正常")
    @GetMapping("/node/status")
    public Map<String, Object> checkNodeStatus(@RequestParam String nodeId) {
        return adminService.checkNodeStatus(nodeId);
    }

    // 2. 提款權限審核 (Withdrawal Approval)
    @Operation(summary = "2. 大額提款權限審核", description = "針對超過閾值的 ETH 提款請求進行二次人工審核")
    @PostMapping("/audit/approve")
    public String approveWithdrawal(@RequestParam String txHash, @RequestParam boolean isApproved) {
        return adminService.approveWithdrawal(txHash, isApproved);
    }

    // 3. 系統緊急停權 (Circuit Breaker)
    @Operation(summary = "3. 系統緊急停權 (緊急狀況)", description = "當偵測到駭客攻擊或智能合約漏洞時，暫停所有對外交易 API")
    @PutMapping("/system/emergency-stop")
    public String emergencyStop() {
        return adminService.emergencyStop();
    }

    // 4. 手續費與 Gas 策略調整 (Gas Strategy)
    @Operation(summary = "4. Gas 策略動態調整", description = "調整系統內部預設的 Gas Price 倍率以確保交易快速上鏈")
    @PostMapping("/config/gas-multiplier")
    public Map<String, Object> updateGasMultiplier(@RequestBody Map<String, Double> config) {
        return adminService.updateGasMultiplier(config);
    }

    // 5. [觸發 500 錯誤測試] 節點連線崩潰模擬
    @Operation(summary = "5. 模擬伺服器異常 (500)", description = "手動拋出異常，用來測試 GlobalExceptionHandler 的 500 錯誤處理")
    @GetMapping("/test/error-500")
    public String triggerError() {
        return adminService.triggerError();
    }

    // 6. [補充] 黑名單地址同步 (Sync Blacklist)
    @Operation(summary = "6. 同步黑名單至智能合約", description = "將本地資料庫的黑名單地址同步至智能合約的 RejectList")
    @PostMapping("/security/sync-blacklist")
    public String syncBlacklist() {
        return adminService.syncBlacklist();
    }

    // 7. [補充] 金鑰庫 (KeyStore) 健康檢查
    @Operation(summary = "7. 金鑰庫安全檢查", description = "檢查 AWS Secrets Manager 中的私鑰讀取權限是否正常")
    @GetMapping("/security/keystore-audit")
    public boolean checkKeyStore() {
        return adminService.checkKeyStore();
    }
}