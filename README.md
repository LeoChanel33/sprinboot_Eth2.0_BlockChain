# ETH 2.0 Blockchain Backend API

以 Java 17、Spring Boot 4.0.3 與 Maven Wrapper 建立的後端示範專案，提供 JPA 使用者資料、節點登錄與外部平台帳號關聯功能。

## 快速啟動

1. 安裝 Java 17，啟動 PostgreSQL。
2. 設定資料庫密碼，例如在 PowerShell 執行 `$env:DB_PASSWORD = '你的資料庫密碼'`。
3. 在專案根目錄執行：

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

也可在 VS Code 選擇 **Spring Boot: Local** 並按 **F5**。詳細設定請見 [VSCODE.md](VSCODE.md)。

啟動後開啟 [Swagger UI](http://localhost:8080/swagger-ui/index.html)，OpenAPI 文件位於 `/v3/api-docs`。
`local` profile 停用 Kafka listener；應用程式仍需要 PostgreSQL，Redis 與 Kafka 測試端點需要各自服務。

## 資料庫設定

設定檔為 [application.properties](src/main/resources/application.properties)。

| 項目 | 預設值 | 可覆寫的環境變數 |
| --- | --- | --- |
| JDBC URL | `jdbc:postgresql://localhost:5432/postgres` | `DB_URL` |
| 帳號 | `postgres` | `DB_USERNAME` |
| 密碼 | 無 | `DB_PASSWORD` |

也可在根目錄建立 `.local/database.properties`，內容為 `spring.datasource.password=你的資料庫密碼`。
此檔由 Spring 自動匯入，已透過 `.gitignore` 排除；環境變數 `DB_PASSWORD` 可優先覆寫密碼。

`spring.jpa.hibernate.ddl-auto=none`，啟動時不會自動建立或修改資料表。
使用 JPA API 前需先建立 Entity 對應的 `user_accounts`、`blockchain_nodes`、`platform_accounts` 資料表與約束，或加入 migration。
目前沒有附正式環境的建表 migration。僅限本機開發、確認目標資料庫後，可自行使用 `ddl-auto=update` 建表。

測試透過 `src/test/resources/application-local.properties` 使用獨立 H2 記憶體資料庫，不會連線至 PostgreSQL。

## 服務架構

```text
controller/
  Admin_APIController  → service/api/AdminApiService
  Client_APIController → service/api/ClientApiService
  Eth_APIController    → service/api/EthApiService

service/api/ClientApiService（業務流程與交易協調）
  ├─ service/jpa/UserJpaService → UserAccountRepository → UserAccount
  ├─ service/jpa/NodeJpaService → BlockchainNodeRepository → BlockchainNode
  └─ service/jpa/CrossPlatformJpaService → PlatformAccountRepository → PlatformAccount

dto/（獨立的請求與回應資料型別）
```

Controller 使用 Lombok `@RequiredArgsConstructor` 與 `private final` 欄位，直接注入對應的 API Service，僅處理 HTTP 參數及回應。
`service/api` 負責業務流程；`service/jpa` 封裝資料驗證與持久化操作；`repository` 負責 JPA 資料存取。
已移除原本的 `Services`／`Sevices` 中轉層，請求與回應型別獨立放在 `dto`，不再依附 Service 類別。
Kafka listener 位於 `ClientApiService`，與 HTTP Controller 分離。
各服務透過 Spring Data `JpaRepository` 存取資料；Entity 使用 Lombok `@Getter` 與 JPA 必要的 protected 無參數建構子，保留指定業務欄位的建構子。

主要流程 `ClientApiService.registerUser(...)` 以 `@Transactional` 在同一筆交易中建立使用者與選填的平台關聯；綁定失敗時註冊也會回滾。
帳號名稱與 email 會去除前後空白並轉小寫，另有重複檢查與資料庫唯一約束。節點狀態更新使用 `@Version` 處理並行寫入衝突。

## JPA API

所有下列端點的前綴為 `/eth/client/api`：

| 方法 | 路徑 | 功能 |
| --- | --- | --- |
| POST | `/users/register` | 註冊，可一併綁定平台 |
| GET | `/users/{userId}` | 查詢使用者 |
| POST | `/nodes` | 登錄節點 |
| GET | `/nodes/{nodeId}` | 查詢節點 |
| PUT | `/nodes/{nodeId}/status` | 更新節點狀態 |
| POST | `/users/{userId}/platforms` | 綁定平台 |
| GET | `/users/{userId}/platforms` | 查詢平台關聯 |

註冊請求（不需要平台時省略 `platform`）：

```json
{
  "username": "leo",
  "email": "leo@example.com",
  "platform": { "platform": "web", "externalAccountId": "web-user-001" }
}
```

節點登錄請求：

```json
{ "nodeId": "sepolia-1", "rpcUrl": "http://localhost:8545", "chainId": 11155111 }
```

狀態更新請求：

```json
{ "status": "ONLINE" }
```

可用狀態為 `REGISTERED`、`SYNCING`、`ONLINE`、`OFFLINE`。
單獨綁定平台的請求為 `{"platform":"web","externalAccountId":"web-user-001"}`。
每個使用者於同一平台只能綁定一個帳號，同一平台的外部帳號也只能綁定一名使用者。

成功建立回傳 201；業務輸入驗證與 JSON 格式錯誤為 400、資料不存在為 404、重複資料或寫入衝突為 409。
例外回應透過 `GlobalExceptionHandler` 統一為 JSON，包含 `status`、`message`、`timestamp`。

## 其他測試端點

| 方法 | 路徑 | 說明 |
| --- | --- | --- |
| GET | `/eth/client/api/redis` | Redis 計數測試，預設 `localhost:6379` |
| GET | `/eth/client/api/kafka` | 發送訊息至 `test-topic`，預設 broker `localhost:9092` |
| GET | `/eth/client/api/contract/info` | 合約與網路資訊示範回傳 |
| GET | `/eth/admin/api/test/error-500` | 刻意拋出例外，驗證 JSON 500 回應 |

## 測試與打包

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

目前包含 7 項測試，涵蓋應用程式啟動、三組 API 路由與服務注入、400 與 JSON 500 回應、註冊與平台綁定、交易回滾、重複帳號與不存在的使用者、節點狀態持久化與輸入驗證。

## 實作範圍

- 使用者註冊目前建立帳號資料，尚未實作密碼、登入、JWT、RBAC 或錢包簽名驗證。
- 跨平台功能目前儲存外部帳號關聯，尚未驗證帳號所有權或串接平台 API。
- 節點功能目前維護登錄與狀態資料，尚未執行 RPC 健康檢查；既有區塊鏈 Controller 多數仍使用示範回傳值。
- 公開部署前需加入身分驗證與存取權限。Web3j、跨鏈、合約部署、WebSocket 與 Kubernetes 自動化屬後續整合方向。
- `compose.yaml` 目前定義 MySQL、Oracle、SQL Server，沒有 PostgreSQL、Redis 或 Kafka，需另行啟動服務。
- `ai_springagent/` 為另一個獨立 Gradle 專案。

## 2026-10-28 更新

- 分開 API 業務服務與 JPA 資料服務，整理獨立 DTO，移除多餘中轉層。
- 三個 Controller 直接以建構子注入 `AdminApiService`、`ClientApiService`、`EthApiService`，業務方法集中於 `service/api`。
- 資料操作封裝於 `service/jpa`，請求與回應型別放入 `dto`；移除 `Services`／`Sevices`，避免名稱與職責混淆。
- 註冊與平台綁定的交易由 `ClientApiService.registerUser(...)` 協調，失敗時一起回滾；Kafka listener 移至 Client 業務服務。
- 執行 Maven `clean test`，7 項測試全部通過，涵蓋三組 HTTP API、錯誤回應與 JPA 交易。
- 新增使用者、節點、平台關聯 API，以及交易與衝突處理。
- 啟用 PostgreSQL JDBC 連線，測試保留獨立 H2；本機密碼移至 Git 忽略的設定檔。
- 更新 README，使啟動流程、功能範圍與專案現況一致。
