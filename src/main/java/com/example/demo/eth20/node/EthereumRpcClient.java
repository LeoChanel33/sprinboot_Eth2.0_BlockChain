package com.example.demo.eth20.node;

import com.example.demo.eth20.config.EthereumNodeProperties;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Read-only RPC operations; no private key or transaction submission on initialization. */
public class EthereumRpcClient {
    private final String nodeId;
    private final EthereumNodeProperties.Node settings;
    private final RestClient http;
    private final AtomicLong sequence = new AtomicLong();

    public EthereumRpcClient(String nodeId, EthereumNodeProperties.Node settings, RestClient http) {
        this.nodeId = nodeId;
        this.settings = settings;
        this.http = http;
    }

    public EthereumNodeProperties.Node settings() {
        return settings;
    }

    public Map<String, Object> info() {
        BigInteger chainId = verifyChainId();
        return Map.of("nodeId", nodeId, "chainId", chainId,
                "clientVersion", rpc("web3_clientVersion", List.of()),
                "latestBlock", quantity(rpc("eth_blockNumber", List.of())),
                "syncing", rpc("eth_syncing", List.of()));
    }

    public BigInteger verifyChainId() {
        BigInteger actual = quantity(rpc("eth_chainId", List.of()));
        if (!actual.equals(BigInteger.valueOf(settings.chainId()))) {
            throw new IllegalStateException("Ethereum chain ID mismatch for " + nodeId
                    + ": expected " + settings.chainId() + ", received " + actual);
        }
        return actual;
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new java.util.LinkedHashMap<>(info());
        Object syncing = status.get("syncing");
        if (!Boolean.FALSE.equals(syncing) && !(syncing instanceof Map)) {
            throw new IllegalStateException("Invalid eth_syncing result from " + nodeId);
        }
        status.put("isSynced", Boolean.FALSE.equals(syncing));
        status.put("peers", quantity(rpc("net_peerCount", List.of())));
        status.put("reachable", true);
        status.put("status", Boolean.FALSE.equals(syncing) ? "ONLINE" : "SYNCING");
        return status;
    }

    public Map<String, Object> balances(String address) {
        if (address != null && !address.matches("0x[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException("A 20-byte Ethereum address is required");
        }
        verifyChainId();
        Object accountsResult = address == null ? rpc("eth_accounts", List.of()) : List.of(address);
        if (!(accountsResult instanceof List<?> accounts)) {
            throw new IllegalStateException("Invalid eth_accounts response");
        }
        java.util.ArrayList<Map<String, Object>> balances = new java.util.ArrayList<>();
        for (Object account : accounts) {
            if (!(account instanceof String value) || !value.matches("0x[0-9a-fA-F]{40}")) {
                throw new IllegalStateException("Invalid account address from node");
            }
            BigInteger wei = quantity(rpc("eth_getBalance", List.of(value, "latest")));
            balances.add(Map.of("address", value, "balanceWei", wei.toString(),
                    "balanceEth", new java.math.BigDecimal(wei, 18).toPlainString()));
        }
        return Map.of("accounts", balances, "accountSource", address == null ? "eth_accounts" : "request",
                "status", balances.isEmpty() ? "NO_ACCOUNTS" : "OK");
    }

    public Map<String, Object> syncStatus() {
        verifyChainId();
        Object syncing = rpc("eth_syncing", List.of());
        if (!Boolean.FALSE.equals(syncing) && !(syncing instanceof Map)) {
            throw new IllegalStateException("Invalid eth_syncing response");
        }
        return Map.of("execution", Map.of("isSynced", Boolean.FALSE.equals(syncing), "syncing", syncing),
                "beacon", optionalQuery(() -> beacon("/eth/v1/node/syncing")));
    }

    public Map<String, Object> identity() {
        return Map.of("chainId", verifyChainId(), "clientVersion", rpc("web3_clientVersion", List.of()),
                "validatorConfigured", settings.validator(),
                "executionIdentity", optionalQuery(() -> rpc("admin_nodeInfo", List.of())),
                "beaconIdentity", optionalQuery(() -> beacon("/eth/v1/node/identity")),
                "beaconVersion", optionalQuery(() -> beacon("/eth/v1/node/version")));
    }

    public Map<String, Object> validators(String validatorId) {
        if (validatorId != null && !validatorId.matches("(?:[0-9]+|0x[0-9a-fA-F]{96})")) {
            throw new IllegalArgumentException("Validator ID must be an index or a 48-byte public key");
        }
        String path = "/eth/v1/beacon/states/head/validators"
                + (validatorId == null ? "" : "/" + validatorId);
        return Map.of("validatorConfigured", settings.validator(), "scope", "BEACON_CHAIN_STATE",
                "validators", beacon(path));
    }

    public Map<String, Object> peers() {
        verifyChainId();
        return Map.of("executionPeerCount", quantity(rpc("net_peerCount", List.of())),
                "beaconPeers", optionalQuery(() -> beacon("/eth/v1/node/peer_count")));
    }

    private Map<?, ?> beacon(String path) {
        String base = settings.beaconRestUrl().toString().replaceAll("/+$", "");
        Map<?, ?> response = http.get().uri(base + path).retrieve().body(Map.class);
        if (response == null || !response.containsKey("data")) {
            throw new IllegalStateException("Invalid Beacon response from " + nodeId);
        }
        return response;
    }

    private Map<String, Object> optionalQuery(java.util.function.Supplier<Object> query) {
        try {
            return Map.of("status", "OK", "result", query.get());
        } catch (org.springframework.web.client.RestClientException | IllegalStateException ex) {
            return Map.of("status", "UNAVAILABLE", "reason", ex.getClass().getSimpleName());
        }
    }

    /** Read a contract with ABI-encoded calldata; signing and deployment are separate concerns. */
    public String callContract(String contractAddress, String encodedData) {
        if (contractAddress == null || !contractAddress.matches("0x[0-9a-fA-F]{40}")
                || encodedData == null || !encodedData.matches("0x(?:[0-9a-fA-F]{2})*")) {
            throw new IllegalArgumentException("A contract address and hex-encoded calldata are required");
        }
        verifyChainId();
        Object result = rpc("eth_call", List.of(Map.of("to", contractAddress, "data", encodedData), "latest"));
        if (!(result instanceof String hex) || !hex.matches("0x(?:[0-9a-fA-F]{2})*")) {
            throw new IllegalStateException("Invalid eth_call result from " + nodeId);
        }
        return hex;
    }

    private Object rpc(String method, List<?> params) {
        long id = sequence.incrementAndGet();
        Map<?, ?> response = http.post().uri(settings.rpcUrl()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params))
                .retrieve().body(Map.class);
        if (response == null || !"2.0".equals(response.get("jsonrpc"))
                || !(response.get("id") instanceof Number responseId) || responseId.longValue() != id) {
            throw new IllegalStateException("Invalid JSON-RPC response from " + nodeId);
        }
        if (response.containsKey("error")) {
            // Do not include full upstream payloads, which may contain sensitive calldata.
            throw new IllegalStateException("Ethereum RPC " + method + " failed for " + nodeId);
        }
        if (response.get("result") == null) {
            throw new IllegalStateException("Missing JSON-RPC result from " + nodeId);
        }
        return response.get("result");
    }

    private static BigInteger quantity(Object value) {
        if (!(value instanceof String hex) || !hex.matches("0x[0-9a-fA-F]+")) {
            throw new IllegalStateException("Invalid Ethereum RPC quantity");
        }
        return new BigInteger(hex.substring(2), 16);
    }
}
