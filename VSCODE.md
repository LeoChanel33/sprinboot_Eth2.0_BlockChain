# 使用 VS Code 開發與啟動

本文件適用於根目錄的 Maven 專案 `eth2.0.application`（Spring Boot 4.0.3、Java 17）。
`ai_springagent/` 是另一個獨立 Gradle 專案，本工作區預設不匯入；開發它時請另外開啟該資料夾。

## 開啟專案

1. 安裝 JDK 17，確認終端機執行 `java -version` 顯示 17，且 `JAVA_HOME` 指向 JDK 目錄。
2. 在 VS Code 選擇「檔案 → 開啟資料夾」，開啟含有 `pom.xml` 的專案根目錄。
3. 安裝工作區推薦的 Extension Pack for Java 與 Spring Boot Extension Pack。
4. 等待 Java 與 Maven 匯入完成。首次使用 Maven Wrapper 需要網路下載 Maven 與相依套件，無需另外安裝 Maven。

## 按 F5 啟動／中斷點除錯

在「執行與偵錯」選擇 **Spring Boot: Local**，按 **F5**。
此設定使用 `local` profile，停用 Kafka listener，無需先準備資料庫、Kafka 或 Redis 即可啟動 HTTP API。
Redis 與 Kafka 測試端點仍需要對應服務；local 模式不提供這些服務的模擬資料。

啟動後開啟：

- Swagger UI：<http://localhost:8080/swagger-ui/index.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>
- 範例 API：<http://localhost:8080/eth/client/api/contract/info>

API 中的區塊鏈資料目前多為示範回傳值。根路徑 `/` 沒有首頁。
按 Shift+F5 停止偵錯；若埠號被占用，可先停止既有服務，或在 launch 設定 args 加入 `--server.port=8081`。

## 在 PowerShell 啟動與建置

在專案根目錄執行（`-D` 參數請保留引號）：

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
.\mvnw.cmd test '-Dspring.profiles.active=local'
.\mvnw.cmd clean package '-Dspring.profiles.active=local'
java -jar target/eth2.0.application-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

終端機執行的服務使用 Ctrl+C 停止。也可以選擇「終端機 → 執行工作」執行 Maven 工作；Ctrl+Shift+B 執行打包。

## 使用 Kafka 與 Redis

先自行啟動 `localhost:9092` 的 Kafka 與 `localhost:6379` 的 Redis，再選擇 **Spring Boot: Full (Kafka + Redis)**。
現有 `compose.yaml` 僅定義資料庫，不會建立 Kafka 或 Redis。
Kafka 的自訂設定目前固定連線至 localhost:9092；若服務位於其他主機，需要修改 `KafkaConfig.java`。

## 常見問題

- 找不到 Java：設定 JDK 17 的 `JAVA_HOME` 與 `PATH`，重新開啟 VS Code；也可使用 `Java: Configure Java Runtime` 檢查。
- 匯入仍顯示舊錯誤：執行 `Java: Clean Java Language Server Workspace`，再重新載入視窗。
- Wrapper 下載失敗：檢查網路、公司代理伺服器及 Maven 設定。
- 中文顯示亂碼：本專案檔案使用 UTF-8，請確認 VS Code 右下角的編碼設定。

Springdoc 已改用與 Spring Boot 4.0.x 對應的 3.0.x 系列，版本對照見 [官方說明](https://springdoc.org/faq.html)。
