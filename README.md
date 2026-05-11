🚀 ETH 2.0 Blockchain Backend API
這是一個基於 Spring Boot 21 構建的高性能區塊鏈後端服務，專為 Ethereum 2.0 網路設計。本專案提供完整的權限控管、智慧合約交互與節點管理功能，並與前端 Nuxt 3 + Vue 3 (nuxt_Vue3_BlockChain) 進行全端整合，最終部署於 AWS Kubernetes (EKS) 環境。

📖 目錄
核心功能

技術棧

API 快速測試

系統架構

部署流程 (Docker & K8s)

全端整合

未來擴充計畫

🛠 核心功能
1. 🔐 ETH 2.0 權限認證 (Identity & Access Management)
JWT 整合安全認證：採用非對稱加密技術驗證使用者身份，確保 API 呼叫安全。

RBAC 權限控管：細分「超級管理員」、「節點營運者」與「普通用戶」角色。

Web3 錢包登入：支援 Web3.js / Ethers.js 進行簽名驗證（Sign-In with Ethereum）。

2. 💸 資產轉帳與申請 (Asset Management)
ETH/ERC-20 轉帳：高度封裝 Web3j 介面，支援 Gas Fee 自動估算與隨機數 (Nonce) 管理。

審核工作流：內建異步審核機制，用於大額轉帳或測試幣撥發的後台管理與 Log 追蹤。

3. 📜 智慧合約與節點部署 (Smart Contract & DevOps)
自動化部署：透過 API 一鍵部署編譯後的 Solidity 合約至 ETH 2.0 測試網/主網。

K8s 節點監控：即時監測運行在 Pod 內的 Beacon Node 與 Execution Node 狀態。

💻 技術棧
後端核心：Spring Boot 3.4 (Java 17)

區塊鏈交互：Web3j, Ethers-Java, Spring-Cloud-Ethereum

資料庫：PostgreSQL (AWS RDS), Redis (ElastiCache)

容器化與編排：Docker, Kubernetes (EKS)

雲端基礎設施：AWS (EC2, EKS, Route53, IAM)

📡 API 快速測試
在專案啟動後，你可以使用以下指令快速驗證服務狀態：

📌 服務健康檢查
Bash
curl -X GET http://localhost:8080/eth/hello
預期回應： ETH 2.0 Module is Running!

📌 取得網路狀態 (範例)
Bash
curl -X GET http://localhost:8080/api/v1/eth/status
🏗 系統架構
本專案採用微服務架構，確保區塊鏈交易的高併發處理能力與數據一致性。

🚢 部署流程 (Docker & K8s)
1. 本地打包與 Docker 化
Bash
# 編譯 Jar 檔
./mvnw clean package -DskipTests

# 構建 Docker 映像檔
docker build -t eth-api:v1.0 .
2. Kubernetes Pod 部署範例
本專案已預載 deployment.yaml，確保 Pod 在 AWS EKS 叢集內具備自動修復與擴展能力：

YAML
apiVersion: apps/v1
kind: Deployment
metadata:
  name: eth-api-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: eth-api
  template:
    metadata:
      labels:
        app: eth-api
    spec:
      containers:
      - name: spring-boot-app
        image: <your-ecr-repo>/eth-api:latest
        ports:
        - containerPort: 8080
🔗 全端整合 (Full-stack Integration)
本專案作為 Nuxt_Vue3_BlockChain 的數據供應核心：

SSR 支援：針對 Nuxt 3 的 Server-side Rendering 提供高速 API 響應。

WebSocket 實時推送：轉帳確認或合約狀態變更時，透過 Socket 實時同步至 Vue 3 前端。

AWS CloudFront：全端靜態與動態內容均通過 AWS CDN 進行加速並配置 CORS 安全策略。

🚀 未來擴充計畫
Layer 2 支援：計畫引進 Arbitrum / Optimism SDK 以大幅降低用戶 Gas 成本。

NFT 市場模組：擴充 ERC-721 與 ERC-1155 的鑄造、掛單與版稅管理功能。

多鏈跨鏈橋：增加對 BSC (Binance Smart Chain) 或 Polygon 的跨鏈數據監測。

## 📅 2026.05.11 更新日誌：基礎設施增強

本次更新完成了核心通訊組件的整合與 Docker 本地環境配置。

### 1. 🚀 Kafka 非同步通訊整合
* **配置優化**：實作 `KafkaConfig`，完成 `ProducerFactory` 與 `ConsumerFactory` 配置。
* **序列化機制**：統一採用 Kafka 官方 `StringSerializer` 與 `StringDeserializer` 以解決類別轉換異常。
* **監聽機制**：實作 `@KafkaListener` 異步監聽 `test-topic`，成功對接區塊鏈交易模擬數據。

### 2. ⚡ Redis 快取與原子操作
* **連線測試**：整合 `StringRedisTemplate` 成功連接 Docker 端的 Redis 服務。
* **計數器實作**：透過 Redis 實作 `/redis` 測試 API，驗證在高併發環境下的原子性計數功能。

### 3. 🐳 Docker 容器化配置
* **編排部署**：完成 Kafka、Zookeeper 與 Redis 的 `docker-compose` 配置。
* **數據持久化**：配置 Named Volumes (如 `redis_data`) 確保容器重啟後數據不丟失。

### 4. 📝 日誌持久化系統
* **自動存檔**：配置 Logback 自動將系統日誌寫入 `logs/eth2_system.log`。
* **滾動策略**：支援按檔案大小 (10MB) 與保留天數 (7天) 自動切分日誌檔案。

---

## 📡 API 快速測試

在專案啟動後，可使用以下指令驗證服務：

| 功能 | 指令 | 預期回應 |
| :--- | :--- | :--- |
| **健康檢查** | `curl -X GET http://localhost:8080/eth/hello` | `ETH 2.0 Module is Running!` |
| **Kafka 測試** | `curl -X GET http://localhost:8080/eth/client/api/kafka` | 觸發 Producer 並由 Listener 印出 Log |
| **Redis 測試** | `curl -X GET http://localhost:8080/eth/client/api/redis` | `Redis 連線成功！目前計數值為: [X]` |

---


👨‍💻 維護者
Project Owner: Leo

GitHub: [Your Profile Link]

Status: Development Phase (Sprint 1)