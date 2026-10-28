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

## 雙節點 Config 與啟動載入

`eth20/config/EthereumNodeConfig` 透過 `@ConfigurationProperties` 載入設定，
建立可注入的 `EthereumNodeRegistry` 與兩個 HTTP RPC client；`ApplicationRunner`
在 Spring Boot 啟動時檢查各節點的 chain ID。設定錯誤會阻止啟動；連線或鏈識別檢查失敗預設記錄警告，
後續呼叫時重新連線與檢查，可設 `ETH_FAIL_FAST=true` 改為檢查失敗就停止啟動。

設定依據為 `C:/eth-sepolia-node/docker-compose.yml` 與
`C:/eth-sepolia-node2/docker-compose.yml`，兩邊 genesis 的 chain ID 均為 `32382`。
雖然目錄名稱包含 sepolia，這兩份主要 Compose 實際設定的是私有 PoS 鏈。

| 節點 | Execution HTTP RPC | Beacon REST | Chain ID | Validator |
| --- | --- | --- | --- | --- |
| node-1 | `http://localhost:8545` | `http://localhost:3500` | 32382 | 有 |
| node-2 | `http://localhost:8546` | `http://localhost:3501` | 32382 | 無，跟隨同步 |

設定集中在 `src/main/resources/application.properties`；Spring 不會在執行時讀取外部 Compose，
也不會建立 Docker 容器、初始化 genesis、匯入私鑰或自動部署合約。
Beacon REST 用於同步、身分、版本、Validator 與 Peer 查詢；validator 欄位表示設定角色，不代表程序健康。啟動檢查使用 Execution HTTP RPC。
`8546` 是第二個節點的 HTTP 映射埠，並非 WebSocket。

可使用環境變數覆寫：

- `ETH_DEFAULT_NODE`：預設 `node-1`，可選 `node-2`。
- `ETH_NODE1_RPC_URL`、`ETH_NODE2_RPC_URL`：Execution HTTP RPC。
- `ETH_NODE1_BEACON_URL`、`ETH_NODE2_BEACON_URL`：Beacon REST。
- `ETH_NODE1_CHAIN_ID`、`ETH_NODE2_CHAIN_ID`：各節點預期的 chain ID。
- `ETH_CHECK_ON_STARTUP`：預設 `true`；測試設定為 `false`，不連實際節點。
- `ETH_FAIL_FAST`：預設 `false`；連線與讀取 timeout 分別為 2 秒、5 秒，可修改 `ethereum.connect-timeout`、`ethereum.request-timeout`。

Spring Boot 在 Windows 主機執行時可使用上表預設值；若部署至 Docker，需依網路環境覆寫 RPC URL，
例如同一個 `eth-private` 網路內使用 `http://geth:8545`、`http://geth-2:8545`，
Beacon 則使用 `http://beacon-chain:3500`、`http://beacon-chain-2:3500`。
另一份 `docker-compose.sepolia.yml` 不在本次預設設定範圍，切換網路時應同步調整 URL 與預期 chain ID。

業務服務可透過建構子注入 `EthereumNodeRegistry`，使用：

```java
nodes.defaultClient().info();
nodes.client("node-2").info();
nodes.client("node-1").callContract(contractAddress, encodedCalldata);
```

`callContract` 以 `eth_call` 唯讀呼叫合約，需自行提供 ABI 編碼後的 calldata，回傳原始 hex；
呼叫前會確認 chain ID。這裡尚未包含 ABI 編解碼、私鑰管理、交易簽名、部署或交易廣播。
`GET /eth/core/api/node/info` 與 `/eth/core/api/node/status` 由 `EthApiService` 獨立查詢，未指定 `nodeId` 時回傳全部設定節點。
此 registry 與 JPA 的節點登錄資料獨立；JPA 登錄不會自動增加 RPC client。

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

## 可注入的 Logger Bean

專案已透過 Spring Boot Web starter 引入 SLF4J 與 Logback，不需另加 logging provider。
`config/LoggingConfig` 提供 prototype scope 的 `org.slf4j.Logger` Bean，依注入位置取得類別名稱，
避免所有 Component 的日誌都顯示同一個 logger 名稱。

```java
@Service
@RequiredArgsConstructor
public class ExampleService {
    private final Logger log; // import org.slf4j.Logger

    public void execute() {
        log.info("Processing node: {}", "node-1");
    }
}
```

可使用 `trace`、`debug`、`info`、`warn`、`error`，以 `{}` 帶入參數，
也可使用 `log.error("Operation failed", exception)` 記錄堆疊。
`AdminApiService`、`ClientApiService` 已改為建構子注入 Logger；既有 `@Slf4j` 也可繼續使用，兩者共用同一套 Logback 設定。
避免將私鑰、密碼等敏感資料寫入 log。

日誌設定沿用 `application.properties`：預設 INFO、輸出至 console 與 `logs/eth2.0-app.log`，
單檔 10MB、保留 7 個歷史期間；可用 `logging.level.完整套件名稱=DEBUG` 調整指定套件。

## 測試與打包

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

測試涵蓋應用程式啟動、三組 API 路由與服務注入、400 與 JSON 500 回應、註冊與平台綁定、交易回滾、重複帳號與不存在的使用者、節點狀態持久化與輸入驗證。
另有雙節點 Config 綁定、預設節點選擇、啟動檢查、chain ID 不符、RPC 錯誤與合約唯讀呼叫測試，使用本機模擬 RPC 伺服器，不依賴 Docker 節點。

## 實作範圍

- 使用者註冊目前建立帳號資料，尚未實作密碼、登入、JWT、RBAC 或錢包簽名驗證。
- 跨平台功能目前儲存外部帳號關聯，尚未驗證帳號所有權或串接平台 API。
- JPA 節點功能維護登錄與狀態資料；RPC Config 另有啟動 chain ID 檢查及真實節點資訊查詢。其他區塊鏈 API 多數仍使用示範回傳值。
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

## sql_data 四張資料表

已新增 eth_admin、eth_client、eth_log、eth_client_detail 的 Entity、Repository、Service 與 CRUD API。
建表腳本為 [database/eth-schema.sql](database/eth-schema.sql)，需在指定 PostgreSQL 手動執行。
完整欄位決策、API 路徑、JSON 範例與更新規則請見 [docs/sql-data-api.md](docs/sql-data-api.md)。
這組 API 與既有三張表的 API 分開，未搬移既有資料。

## 節點監控 API

下列 API 依注入的 `EthereumNodeRegistry` 查詢，不依賴 JPA 登錄資料。未指定 `nodeId` 時逐一查詢所有設定節點，回傳 `{"nodes":[...]}`；指定 `?nodeId=node-1` 或 `?nodeId=node-2` 則回傳單一節點物件。Core 與 Admin 各使用自己的 API Service。

| GET 路徑 | 功能 | 選填參數 |
| --- | --- | --- |
| `/eth/admin/api/node/status` | 區塊、同步、Peer 與連線狀態 | `nodeId` |
| `/eth/core/api/node/status` | Core 節點狀態 | `nodeId` |
| `/eth/core/api/node/info` | Core 節點狀態的另一個路徑 | `nodeId` |
| `/eth/admin/api/node/balance` | 精確 Wei 與 ETH 餘額字串 | `nodeId`、`address` |
| `/eth/admin/api/node/sync` | Execution 與 Beacon 同步資訊 | `nodeId` |
| `/eth/admin/api/node/identity` | Chain ID、版本與節點身分 | `nodeId` |
| `/eth/admin/api/node/validators` | Beacon 鏈上 Validator 狀態與餘額 | `nodeId`、`validatorId` |
| `/eth/admin/api/node/peers` | Execution 與 Beacon Peer 數量 | `nodeId` |

```powershell
Invoke-RestMethod 'http://localhost:8080/eth/admin/api/node/status'
Invoke-RestMethod 'http://localhost:8080/eth/admin/api/node/sync?nodeId=node-2'
Invoke-RestMethod 'http://localhost:8080/eth/admin/api/node/balance?address=0x0000000000000000000000000000000000000000'
Invoke-RestMethod 'http://localhost:8080/eth/admin/api/node/validators?validatorId=0'
```

ETH 餘額屬於帳戶地址。省略 `address` 時查詢 `eth_accounts` 公開的帳戶；空清單回傳 `NO_ACCOUNTS`，不代表鏈上沒有 ETH。指定地址時讀取各節點 `latest` 狀態下的餘額。

Execution 的 `eth_syncing=false` 不足以證明整條 PoS 鏈健康。請同時檢查 Beacon 的 `is_syncing`、`sync_distance`、`is_optimistic` 與 `el_offline`。`ONLINE` 表示 Execution RPC 查詢成功且未回報同步中，不表示持續出塊。

`validatorId` 接受索引或 48-byte 公鑰，省略時列出 Beacon 鏈上的 Validator。兩個節點可能回傳相同鏈上資料；這不是各節點持有的 Validator 金鑰清單，也不是本機 Validator 程序健康檢查。原始 Beacon 餘額單位為 Gwei。

身分查詢中的 `admin_nodeInfo` 需要上游支援，Beacon 身分也可能回傳錯誤。選填查詢失敗會標示 `UNAVAILABLE`，並保留已取得的版本等資料。五組新增監控查詢會分別處理各節點的上游錯誤；無效的節點名稱、地址或 Validator ID 回傳 400。

## 啟動與查詢日誌

Console 與 `logs/eth2.0-app.log` 的時間格式為 `yyyy-MM-dd HH:mm:ss.SSS`，使用執行主機時區。
`StartupLogging` 記錄應用程式 Bean 初始化及依賴注入完成，以及應用程式啟動完成耗時。節點查詢也會記錄完成或失敗。

## 常見問題

- PostgreSQL `Connection refused`：確認資料庫已啟動，以及 `DB_URL` 的主機與連接埠；後續 Dialect 錯誤可能是連線失敗的連帶結果。
- 節點 `UNAVAILABLE`：檢查容器、主機連接埠映射與 RPC URL；容器運作不表示 RPC 已準備完成。
- Beacon `el_offline=true`：檢查 Beacon 與 Execution 的 Engine API、JWT 設定與容器網路。
- 資料表不存在：`ddl-auto=none`，四張 eth 資料表需先執行 `database/eth-schema.sql`，既有三張表則需另外準備。
