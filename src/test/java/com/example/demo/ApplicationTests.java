package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class ApplicationTests {

    @Value("${local.server.port}")
    private int port;

	@Test
	void contextLoads() {
	}

    @Test
    void apiRoutesReachTheirInjectedBusinessServices() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String base = "http://localhost:" + port;

        var admin = client.send(HttpRequest.newBuilder()
                .uri(URI.create(base + "/eth/admin/api/node/status?nodeId=node-1"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, admin.statusCode());
        assertTrue(admin.body().contains("\"nodeId\":\"node-1\""));

        var user = client.send(HttpRequest.newBuilder()
                .uri(URI.create(base + "/eth/client/api/contract/info"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, user.statusCode());
        assertTrue(user.body().contains("Sepolia"));

        var eth = client.send(HttpRequest.newBuilder()
                .uri(URI.create(base + "/eth/core/api/contract/deploy?contractName=demo"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, eth.statusCode());
        assertTrue(eth.body().contains("\"contractName\":\"demo\""));

        var invalidKey = client.send(HttpRequest.newBuilder()
                .uri(URI.create(base + "/eth/core/api/wallet/auth"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"privateKey\":\"invalid\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, invalidKey.statusCode());
        assertTrue(invalidKey.body().contains("\"status\":400"));
    }

    @Test
    void serverErrorReturnsJsonEvenForBrowserRequests() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/eth/admin/api/test/error-500"))
                .header("Accept", "text/html")
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(500, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("")
                .startsWith("application/json"));
        assertTrue(response.body().contains("\"status\":500"));
        assertTrue(response.body().contains("系統發生異常，請聯繫管理員"));
        assertTrue(response.body().contains("\"timestamp\":"));
    }

}
