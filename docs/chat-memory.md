# 聊天長期記憶與 Session

## 現在的流程

1. 登入後建立 WebSocket，前端送出 `memory.open`。後端辨認記憶擁有者、載入歷史主題清單，並將最近一段對話的近期內容載入 WebSocket Session attributes。沒有歷史時建立空白短期 Session，不寫入空對話。
2. 前端呈現歷史主題、詢問類別、主要索引與更新時間。可搜尋、分類、分頁、選取舊對話或建立新主題。最新一段對話預先恢復，其他對話按需拉回。
3. 前端只送本次問題；Ollama 的上下文由後端 Session 組裝，不再相信前端提供的歷史問答。模型收到系統規則、登入背景、近期完整對話和本次問題。
4. 問題由應用程式寫入 Session，先建立資料庫檢查點；回覆完成後將問答寫入同一對話。分類與儲存由程式保證，不依賴 LLM 記得呼叫工具。
5. 登出按鈕呼叫後端 `memory.logout`。若推論中，先停止並保存已收到的回覆片段，標記為 `INTERRUPTED`；沒有片段則標記 `FAILED`。交易提交後才清除 Session attributes，再回應 `saved:true`；前端收到確認後才清除登入狀態與畫面。
6. 儲存失敗時保留登入狀態、Session 中待儲存資料，讓使用者重試。重試以訊息 UUID 去重，避免重複寫入。每輪也做檢查點，避免完全依賴分頁關閉或登出通知。
7. 一般斷線會停止推論並嘗試儲存／清除 Session；突然斷電或程序終止時，已完成的檢查點仍在，最後尚未提交的串流片段可能遺失。重新連線會重新載入歷史，不自動重送問題。

這裡的 Session 是 **後端 WebSocket Session attributes**，不是瀏覽器 sessionStorage，也不是 Ollama 記憶體。Session 只存在當前後端程序與連線，跨程序的長期資料以 MySQL 為準；Ollama 模型權重沒有被修改。

## 身分隔離

目前專案仍採前端模擬錢包登入，沒有真正的伺服器登入驗證。本機 `local` profile 以 `chat.memory.allow-demo=true` 啟用示範：瀏覽器產生隨機 256-bit 識別碼並存於 localStorage，搭配帳號衍生 owner key，只有相同帳號及識別碼能存取那份歷史。資料庫只存 SHA-256 owner key，不存原始識別碼。

這不是正式帳號認證：同一瀏覽器的持有者仍可修改前端帳號，清除瀏覽器資料也會失去該份示範歷史的存取方式；不可將此模式用在多人共用電腦或公開服務。跨裝置不會自動共用紀錄。

正式模式預設 `chat.memory.allow-demo=false`：必須有握手階段由伺服器驗證產生的 Principal，owner key 根據 Principal 的穩定使用者 ID 派生。前端自述帳號、等級、錢包不得作為授權依據。既有 `CHAT_REQUIRE_AUTH=true` 也可要求在握手後拒絕匿名連線，但仍須另外實作真正登入驗證。所有歷史讀取、載入與寫入都用 owner key 限制，不接受前端指定別人的 owner key。

## 資料表與分類

DDL：後端 `database/chat-memory-schema.sql`，對現有 MySQL `eth20` 新增兩張表：

| 表 | 重要欄位 |
| --- | --- |
| `chat_memory_conversation` | 對話 UUID、owner key、帳號、主題、類別、主要索引、建立／更新時間 |
| `chat_memory_message` | 訊息 UUID、全域遞增 sequence、對話 ID、角色、文字、每次詢問類別／主要索引、完成狀態、時間 |

主題可自行填寫；留空則取第一個問題前 80 字。主題主要索引留空時以第一個問題的正規化文字前 120 字建立。每個問題也各自記錄分類和主要索引，對應的 AI 回覆沿用該問題索引。

類別為 `general/account/wallet/transfer/blockchain/preference`。初版以明確關鍵字規則分類，不宣稱是 LLM 語意分類；主題分類可在建立時選擇。列表依類別篩選時也檢查對話內問題分類；文字搜尋涵蓋主題、主題索引及問題索引。索引是可追溯文字鍵與 SQL 索引，不是向量檢索。RAG 表保持獨立，本次沒有把聊天內容自動灌入共用 RAG。

已保存的偏好文字會在恢復該段對話後提供給模型；尚未自動提煉成跨所有主題的使用者偏好檔案或摘要。初版不混合不同主題的全部歷史，以免帶入錯誤背景。過長的舊內容可在畫面分頁查看，但不會全量塞進模型。

## WebSocket 契約

所有請求有唯一 `id`；記憶操作成功回傳 `{id,type:"memory",data:...}`，失敗回傳 `{id,type:"error",message:...}`。

| type | 請求欄位 | 成功資料 |
| --- | --- | --- |
| `memory.open` | `userContext`、本機模式 `deviceKey` | 主題清單、分頁、identityMode、可選的 `current` 最近對話 |
| `memory.list` | `category`、`query`、`offset` | conversations、hasMore |
| `memory.load` | conversationId | 最近 50 則訊息、主題資訊、hasMore |
| `memory.older` | beforeSequence | 目前對話更早的 50 則訊息、hasMore |
| `memory.new` | 可選 title、category、primaryIndex | 新對話 ID 與空訊息清單 |
| `memory.logout` | 無 | saved:true；已儲存並移除後端 Session |
| `chat` | message（1～4000 字）、userContext | 維持原有 delta/done/error 串流 |

`userContext` 於 open 複製白名單，排除密碼和 token；Session 期間以初始化背景為準，修改背景須重新連線。每頁 50 個主題／訊息；切換主題時禁止同時推論。訊息 sequence 以字串回傳，避免 JavaScript 整數精度問題。跨分頁插入新主題可能使 offset 分頁出現重複／位移，重新搜尋可刷新列表；訊息使用 sequence 游標。

Session 上限 200 則、總文字約 100000 Java 字元，推論使用最近至多 20 則已完成訊息並限制歷史加問題約 48000 字元；這是字元限制，不是精確 token 計數，模型輸入窗較小時仍需調整。原文完整保存在資料庫，不因 Session 截短而刪除。空閒 WebSocket 30 分鐘會關閉並走斷線儲存流程。既有推論上限 120 秒；歷史資料目前沒有自動到期或前端刪除功能。

## 執行與驗證

先將 SQL 套用到已選取的 `eth20`，再啟動更新後的 Spring Boot。舊客戶端只送 messages 的協定已被新的 Session 管理流程取代；請重新整理 Nuxt 頁面。

前端：`npm.cmd run test:chat`、`npm.cmd run build`。後端：`mvnw.cmd "-Dtest=ChatMemoryServiceTest,ChatSocketHandlerTest" test`。

元件測試涵蓋登入查歷史、選取對話、前端不自帶歷史上下文、串流回覆、登出等待儲存確認、失敗維持登入、再次登入與識別碼持續。後端隔離資料庫測試涵蓋使用者與識別碼隔離、權限拒絕、登出清除 Session、儲存失敗保留資料與重試去重、分類索引、歷史分頁與上下文上限。另已用執行中的 WebSocket → Ollama → MySQL 實測完整往返及不同識別碼的存取拒絕。
