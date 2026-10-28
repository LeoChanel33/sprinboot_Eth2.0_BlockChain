package com.example.demo.eth20;

import com.example.demo.eth20.config.EthereumNodeConfig;
import com.example.demo.eth20.config.EthereumNodeProperties;
import com.example.demo.eth20.node.EthereumNodeRegistry;
import com.example.demo.eth20.node.EthereumRpcClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import com.example.demo.eth20.service.api.AdminApiService;
import org.slf4j.LoggerFactory;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EthereumNodeConfigTests {
    private HttpServer server;
    private URI rpcUrl;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> chainResult = new AtomicReference<>("\"0x7e7e\"");
    private final AtomicReference<String> error = new AtomicReference<>();
    private final AtomicReference<String> syncing = new AtomicReference<>("false");

    @BeforeEach
    void startRpcServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(request);
            var id = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(request);
            if (!id.find()) throw new IllegalStateException("Missing RPC request ID");
            String result = request.contains("eth_chainId") ? chainResult.get() : "\"0x1234\"";
            if (request.contains("eth_syncing")) result = syncing.get();
            if (request.contains("net_peerCount")) result = "\"0x2\"";
            if (request.contains("eth_accounts")) result = "[\"0x" + "1".repeat(40) + "\"]";
            if (request.contains("eth_getBalance")) result = "\"0xde0b6b3a7640001\"";
            String payload = "{\"jsonrpc\":\"2.0\",\"id\":" + id.group(1) + ","
                    + (error.get() == null ? "\"result\":" + result : "\"error\":" + error.get()) + "}";
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        rpcUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopRpcServer() {
        server.stop(0);
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withUserConfiguration(EthereumNodeConfig.class)
                .withPropertyValues("ethereum.default-node=node-2", "ethereum.connect-timeout=1s",
                        "ethereum.request-timeout=1s", "ethereum.check-on-startup=true",
                        "ethereum.nodes.node-1.rpc-url=" + rpcUrl,
                        "ethereum.nodes.node-1.beacon-rest-url=http://localhost:3500",
                        "ethereum.nodes.node-1.chain-id=32382",
                        "ethereum.nodes.node-2.rpc-url=" + rpcUrl,
                        "ethereum.nodes.node-2.beacon-rest-url=http://localhost:3501",
                        "ethereum.nodes.node-2.chain-id=32382");
    }

    private EthereumRpcClient client() {
        return new EthereumRpcClient("node-1",
                new EthereumNodeProperties.Node(rpcUrl, URI.create("http://localhost:3500"), 32382, true),
                RestClient.create());
    }

    @Test
    void balancesPreserveWeiPrecisionAndValidateAddresses() {
        Map<?, ?> account = (Map<?, ?>) ((List<?>) client().balances(null).get("accounts")).get(0);
        assertEquals("1000000000000000001", account.get("balanceWei"));
        assertEquals("1.000000000000000001", account.get("balanceEth"));
        assertTrue(requests.stream().anyMatch(request -> request.contains("eth_accounts")));
        requests.clear();
        assertThrows(IllegalArgumentException.class, () -> client().balances("invalid"));
        assertTrue(requests.isEmpty());
    }

    @Test
    void monitoringIsolatesRpcFailuresAndRejectsInvalidFilters() {
        var service = new AdminApiService(LoggerFactory.getLogger(AdminApiService.class),
                new EthereumNodeRegistry("node-1", Map.of("node-1", client())));
        error.set("{\"code\":-32000,\"message\":\"failure\"}");
        var results = (List<?>) service.queryNodes(null, AdminApiService.NodeQuery.PEERS, null).get("nodes");
        assertEquals("UNAVAILABLE", ((Map<?, ?>) results.get(0)).get("status"));
        assertThrows(IllegalArgumentException.class,
                () -> service.queryNodes(null, AdminApiService.NodeQuery.VALIDATORS, "../invalid"));
    }

    @Test
    void adminStatusReadsRpc() {
        var service = new AdminApiService(LoggerFactory.getLogger(AdminApiService.class),
                new EthereumNodeRegistry("node-1", Map.of("node-1", client())));
        var status = service.checkNodeStatus("node-1");
        assertEquals("node-1", status.get("nodeId"));
        assertEquals(true, status.get("isSynced"));
        assertEquals(new java.math.BigInteger("4660"), status.get("latestBlock"));
        assertEquals(new java.math.BigInteger("2"), status.get("peers"));
        syncing.set("{\"currentBlock\":\"0x1\",\"highestBlock\":\"0x2\"}");
        assertEquals("SYNCING", service.checkNodeStatus("node-1").get("status"));
        assertThrows(IllegalArgumentException.class, () -> service.checkNodeStatus("missing"));
        error.set("{\"code\":-32000,\"message\":\"failure\"}");
        assertThrows(IllegalStateException.class, () -> service.checkNodeStatus("node-1"));
    }

    @Test
    void omittedNodeIdChecksAllRegisteredNodes() {
        context().run(ctx -> {
            var service = new AdminApiService(LoggerFactory.getLogger(AdminApiService.class),
                    ctx.getBean(EthereumNodeRegistry.class));
            var result = service.checkNodeStatus(null);
            var statuses = (List<?>) result.get("nodes");
            assertEquals(2, statuses.size());
            assertEquals("node-1", ((Map<?, ?>) statuses.get(0)).get("nodeId"));
            assertEquals("node-2", ((Map<?, ?>) statuses.get(1)).get("nodeId"));
            assertEquals(10, requests.size());
        });
    }

    @Test
    void bindsBothNodesSelectsDefaultAndChecksChainOnStartup() {
        context().run(ctx -> {
            assertNull(ctx.getStartupFailure());
            var nodes = ctx.getBean(EthereumNodeRegistry.class);
            assertEquals(2, nodes.clients().size());
            assertSame(nodes.client("node-2"), nodes.defaultClient());
            assertThrows(IllegalArgumentException.class, () -> nodes.client("missing"));
            ctx.getBean(ApplicationRunner.class).run(new DefaultApplicationArguments());
            assertEquals(2, requests.size());
            assertTrue(requests.stream().allMatch(request -> request.contains("eth_chainId")));
        });
    }

    @Test
    void rejectsChainMismatchBeforeContractCall() {
        chainResult.set("\"0x1\"");
        assertThrows(IllegalStateException.class,
                () -> client().callContract("0x" + "1".repeat(40), "0x1234"));
        assertEquals(1, requests.size());
        assertFalse(requests.get(0).contains("eth_call"));
    }

    @Test
    void contractReadUsesConfiguredEndpointAndEncodedCalldata() {
        assertEquals("0x1234", client().callContract("0x" + "1".repeat(40), "0xabcdef00"));
        assertEquals(2, requests.size());
        assertTrue(requests.get(1).contains("eth_call"));
        assertTrue(requests.get(1).contains("0xabcdef00"));
        assertTrue(requests.get(1).contains("latest"));
    }

    @Test
    void rpcErrorDoesNotBecomeSuccessfulResult() {
        error.set("{\"code\":-32000,\"message\":\"failure\"}");
        assertThrows(IllegalStateException.class, () -> client().verifyChainId());
    }

    @Test
    void startupCanContinueOrFailFastWhenChainIsWrong() {
        chainResult.set("\"0x1\"");
        context().withPropertyValues("ethereum.fail-fast=false").run(ctx ->
                assertDoesNotThrow(() -> ctx.getBean(ApplicationRunner.class).run(new DefaultApplicationArguments())));
        context().withPropertyValues("ethereum.fail-fast=true").run(ctx ->
                assertThrows(IllegalStateException.class,
                        () -> ctx.getBean(ApplicationRunner.class).run(new DefaultApplicationArguments())));
    }
}
