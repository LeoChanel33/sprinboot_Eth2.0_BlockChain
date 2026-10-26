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
