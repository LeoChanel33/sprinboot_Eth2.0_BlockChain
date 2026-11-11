# sql_data 資料表與 CRUD API

依照根目錄 sql_data.txt 建立四組獨立 Entity、Repository、Service、Controller。
既有 user_accounts、blockchain_nodes、platform_accounts 與原 API 保留；本次沒有搬移舊資料。

## 建立 MySQL 資料表

確認連線目標後，在專案根目錄執行：

```powershell
Get-Content -Raw database/eth-schema.sql | docker exec -i mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"'
```

容器使用 .local/docker-mysql.env 的本機密碼。首次初始化空的 MySQL volume 時會自動建立資料庫及執行建表腳本；Spring Boot 啟動本身不會建表。MySQL DDL 不具整批交易回滾效果，腳本不包含 DROP。
資料表已存在時腳本會失敗；請先比較既有 schema，勿直接重複執行。
Spring Boot 仍使用 DB_URL、DB_USERNAME、DB_PASSWORD 或既有 .local/database.properties。
不會將 sql_data.txt 的資料庫密碼複製到程式碼。

## 資料型別與約束

- eth_admin、eth_client：id 使用 MySQL INTEGER AUTO_INCREMENT；account 必填且唯一，保留大小寫；password 必填。
- password 僅接受寫入，使用 PBKDF2-HMAC-SHA256（600000 次、隨機 16-byte salt、256-bit hash）儲存，查詢不回傳密碼或 hash。本次不新增登入驗證功能。
- eth_log：原檔未指定主鍵，補上 INTEGER IDENTITY id；六個 log 欄位使用 TEXT。
- eth_client_detail：hash_key 使用 VARCHAR(255) 主鍵；不額外假設與 eth_client 的外鍵關係。
- 其他字串使用 VARCHAR(255)，date 使用 DATE / LocalDate（yyyy-MM-dd），cash 依原規格使用 DOUBLE / Double。
- createDate 未提供時預設當天；更新未提供時保留原值。detail.modifyDate 由伺服器設定為當天。
- cash 使用浮點數，若後續需要精確金額運算，需另外確認並改成 NUMERIC / BigDecimal。

## HTTP 合約

Swagger UI：/swagger-ui/index.html；由 Controller 與 Entity 產生的 OpenAPI：/v3/api-docs。

| 資源 | 路徑 | 單筆主鍵 |
| --- | --- | --- |
| 管理員 | /api/eth/admins | Integer id |
| 客戶 | /api/eth/clients | Integer id |
| 操作紀錄 | /api/eth/logs | Integer id |
| 客戶明細 | /api/eth/client-details | String hashKey |

每組支援：

| 方法 | 路徑 | 回應 |
| --- | --- | --- |
| GET | 資源路徑?page=0&size=20 | 200，Spring Page，content 為資料陣列 |
| GET | 資源路徑/{id} | 200；不存在為 404 |
| POST | 資源路徑 | 201，建立後資料 |
| PUT | 資源路徑/{id} | 200，更新後資料；不存在為 404 |
| DELETE | 資源路徑/{id} | 204，無內容；不存在為 404 |

page 從 0 開始，size 為 1..100，依主鍵排序。錯誤參數為 400；唯一約束衝突為 409。
POST 的流水號 id 不可指定；detail POST 必須提供 hashKey。
PUT 的主鍵以 URL 為準；其他欄位採完整取代，未提供的選填欄位清空。
例外：password 省略時保留原 hash，createDate 省略時保留原日期。
PUT 的 account 仍必填。password 空白視為錯誤，最長 1024 字元。
目前沿用專案現有存取方式，未新增身分驗證與角色授權；level 是儲存欄位。

JSON 使用 camelCase，資料庫使用 sql_data.txt 的 snake_case 名稱。

新增客戶：

```json
{
  "account": "client001",
  "password": "example-password",
  "level": 1,
  "createAccount": "admin001",
  "mail": "client@example.com",
  "phone": "0912345678",
  "address": "Taipei",
  "hashKey": "wallet001",
  "mainCompany": "Example",
  "mainAccount": "parent001"
}
```

新增明細：

```json
{"hashKey":"wallet001","cash":100.5,"agreedAccount":"admin001"}
```

新增紀錄：

```json
{"registerLog":"client001 registered","modifyLog":"profile updated"}
```

## 驗證

EthCrudIntegrationTests 以 Spring Boot 隨機 HTTP port 和獨立 H2 執行 API 測試。
涵蓋四資源 CRUD、唯一約束、主鍵、密碼雜湊與回應隱藏、分頁限制、404 與缺少密碼。
這些測試不會連線或修改本機 MySQL；H2 相容模式不能代替真實 MySQL 驗證。