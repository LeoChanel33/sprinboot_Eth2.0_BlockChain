package com.example.demo.eth20.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import ch.qos.logback.classic.Logger;

import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * ETH 2.0 client API (Client API) Integrate Swagger OpenAPI 3 specification
 */
@RestController
@RequestMapping("eth/client/api")
@Tag(name = "ETH 使用者操作介面", description = "包含錢包管理、轉帳、合約交互與節點認證功能")
public class Client_APIController {

	private static final Logger log = (Logger) LoggerFactory.getLogger(Client_APIController.class);
	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@GetMapping("/redis")  //redis測試
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

	@GetMapping("/kafka")  //kafka測試
	public String testKafka() {
		String mockData = "{'transaction': '0xabc123', 'amount': '1.5 ETH', 'status': 'PENDING'}";

		// 注意：這裡的 Topic 名稱要跟你的 @KafkaListener 一致
		kafkaTemplate.send("test-topic", mockData);

		return "Kafka 訊息已發送至 eth-events Topic！請檢查 Console 日誌。";
	}

	@KafkaListener(topics = "test-topic", groupId = "leo-group")  //kafka監聽
	public void listen(String message) {
		log.info("【Kafka 監聽成功】接收到 ETH 交易數據: {}", message);
		System.out.println("收到 Kafka 訊息並開始處理: " + message);
		// 如果是錯誤資訊可以用 log.error
		if (message.contains("ERROR")) {
			log.error("偵測到異常交易狀態！內容: {}", message);
		}
	}

	@Operation(summary = "1. 查詢錢包餘額", description = "輸入 ETH 錢包地址，從節點獲取即時餘額 (ETH)")
	@GetMapping("/balance/{address}")
	public Map<String, Object> getBalance(
			@Parameter(description = "ETH 錢包地址 (0x...)", example = "0x742d35Cc6634C0532925a3b844Bc454e4438f44e") @PathVariable String address) {
		Map<String, Object> response = new HashMap<>();
		response.put("address", address);
		response.put("balance_eth", "1.25");
		response.put("status", "success");
		return response;
	}

	@Operation(summary = "2. 發起 ETH 轉帳", description = "處理私鑰簽章並發送 Raw Transaction 到區塊鏈")
	@PostMapping("/transfer")
	public Map<String, String> transferEth(@RequestBody Map<String, String> request) {
		Map<String, String> res = new HashMap<>();
		res.put("txHash", "0xabc123... (此為模擬 Hash)");
		res.put("message", "交易已提交至 ETH 2.0 節點，等待區塊確認");
		return res;
	}

	@Operation(summary = "3. 智能合約提款", description = "呼叫部署在 K8s Pod 節點上的智慧合約 withdraw 函數")
	@PostMapping("/contract/withdraw")
	public String withdrawFromContract(@RequestParam String contractAddress) {
		return "智慧合約提款請求已發送至地址: " + contractAddress;
	}

	@Operation(summary = "4. 更改帳戶存取密碼", description = "更新資料庫中用於保護私鑰 KeyStore 的二次驗證密碼")
	@PutMapping("/account/password")
	public String updatePassword(@RequestBody Map<String, String> pwdData) {
		return "安全性密碼更新成功";
	}

	@Operation(summary = "5. 地理位置與錢包認證", description = "全端整合：驗證使用者當前 IP 位置與 Web3 錢包簽章的一致性")
	@PostMapping("/verify/location")
	public Map<String, Object> verifyLocation(@RequestBody Map<String, Object> locationData) {
		Map<String, Object> result = new HashMap<>();
		result.put("isVerified", true);
		result.put("timestamp", System.currentTimeMillis());
		return result;
	}

	@Operation(summary = "6. 新增約定帳戶", description = "將常用的 ETH 地址新增至白名單（Whitelist），減少誤轉風險")
	@PostMapping("/account/trusted")
	public String addTrustedAccount(@RequestParam String alias, @RequestParam String address) {
		return "成功新增約定帳戶: " + alias + " (" + address + ")";
	}

	@Operation(summary = "7. 封鎖帳戶/黑名單", description = "在本地系統封鎖特定異常地址，並停止與其合約交互")
	@DeleteMapping("/account/block/{address}")
	public String blockAccount(@PathVariable String address) {
		return "地址 " + address + " 已被系統封鎖";
	}

	@Operation(summary = "8. 智慧合約與節點資訊查詢", description = "獲取目前部署的合約版本與節點同步狀態")
	@GetMapping("/contract/info")
	public Map<String, String> getContractInfo() {
		Map<String, String> info = new HashMap<>();
		info.put("network", "ETH 2.0 Testnet (Sepolia)");
		info.put("node_status", "Synced");
		return info;
	}

	@Operation(summary = "9. Gas 費用預估", description = "動態計算當前區塊鏈網路擁塞情況下的交易手續費")
	@PostMapping("/gas/estimate")
	public Map<String, String> estimateGas() {
		Map<String, String> gas = new HashMap<>();
		gas.put("low", "15 Gwei");
		gas.put("market", "25 Gwei");
		gas.put("fast", "40 Gwei");
		return gas;
	}
}