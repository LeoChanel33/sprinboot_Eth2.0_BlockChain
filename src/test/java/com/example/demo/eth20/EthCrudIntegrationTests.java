package com.example.demo.eth20;

import com.example.demo.eth20.entity.*;
import com.example.demo.eth20.repository.*;
import com.example.demo.eth20.service.jpa.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class EthCrudIntegrationTests {
    @LocalServerPort int port;
    @Autowired EthAdminRepository admins;
    @Autowired EthClientRepository clients;
    @Autowired EthLogRepository logs;
    @Autowired EthClientDetailRepository details;
    private final HttpClient http = HttpClient.newHttpClient();

    HttpResponse<String> request(String method, String path, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/eth/" + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void schemaScriptCreatesAllTablesInPostgresCompatibilityMode() throws Exception {
        try (var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:eth_schema;MODE=PostgreSQL", "sa", "");
             var statement = connection.createStatement()) {
            String sql = java.nio.file.Files.readString(java.nio.file.Path.of("database/eth-schema.sql"));
            for (String command : sql.split(";"))
                if (!command.isBlank()) statement.execute(command);
            for (String table : java.util.List.of("eth_admin", "eth_client", "eth_log", "eth_client_detail"))
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(result.next());
                    assertEquals(0, result.getInt(1));
                }
        }
    }
    @Test void accountLifecycleAndPasswordProtection() throws Exception {
        admins.deleteAll();
        var created = request("POST", "admins", """
                {"account":"test-admin","password":"test-password","level":1,"mail":"a@example.com"}
                """);
        assertEquals(201, created.statusCode(), created.body());
        assertFalse(created.body().contains("password"));
        var saved = admins.findAll().get(0);
        assertTrue(saved.getPassword().startsWith("pbkdf2-sha256$600000$"));
        assertNotEquals("test-password", saved.getPassword());
        String hash = saved.getPassword();
        String path = "admins/" + saved.getId();
        assertEquals(409, request("POST", "admins", """
                {"account":"test-admin","password":"another-password"}
                """).statusCode());
        assertEquals(200, request("PUT", path, """
                {"account":"updated-admin","level":2}
                """).statusCode());
        assertEquals(hash, admins.findById(saved.getId()).orElseThrow().getPassword());
        assertEquals(200, request("GET", path, null).statusCode());
        assertEquals(400, request("GET", "admins?size=101", null).statusCode());
        assertEquals(204, request("DELETE", path, null).statusCode());
        assertEquals(404, request("GET", path, null).statusCode());
        assertEquals(400, request("POST", "admins", """
{"account":"missing-password"}
""").statusCode());
    }
    @Test void clientLogAndDetailLifecycle() throws Exception {
        clients.deleteAll(); logs.deleteAll(); details.deleteAll();
        assertEquals(201, request("POST", "clients", """
                {"account":"client","password":"secret","mainCompany":"company","mainAccount":"parent"}
                """).statusCode());
        var client = clients.findAll().get(0);
        assertEquals("company", client.getMainCompany());
        assertEquals(200, request("PUT", "clients/" + client.getId(), """
                {"account":"client-updated","mainCompany":"new"}
                """).statusCode());
        assertEquals(204, request("DELETE", "clients/" + client.getId(), null).statusCode());
        assertEquals(201, request("POST", "logs", """
{"registerLog":"registered"}
""").statusCode());
        var log = logs.findAll().get(0);
        assertEquals(200, request("PUT", "logs/" + log.getId(), """
{"modifyLog":"changed"}
""").statusCode());
        assertEquals("changed", logs.findById(log.getId()).orElseThrow().getModifyLog());
        assertEquals(204, request("DELETE", "logs/" + log.getId(), null).statusCode());
        assertEquals(201, request("POST", "client-details", """
                {"hashKey":"wallet-test","cash":12.5,"agreedAccount":"approver"}
                """).statusCode());
        assertEquals(409, request("POST", "client-details", """
{"hashKey":"wallet-test"}
""").statusCode());
        assertEquals(200, request("PUT", "client-details/wallet-test", """
{"cash":20.5}
""").statusCode());
        var detail = details.findById("wallet-test").orElseThrow();
        assertEquals(20.5, detail.getCash());
        assertNotNull(detail.getCreateDate());
        assertNotNull(detail.getModifyDate());
        assertEquals(204, request("DELETE", "client-details/wallet-test", null).statusCode());
        assertEquals(404, request("GET", "client-details/wallet-test", null).statusCode());
    }
}