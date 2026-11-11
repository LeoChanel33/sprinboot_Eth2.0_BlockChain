package com.example.demo.chat;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class ChatMemoryServiceTest {
    final ObjectMapper json = new ObjectMapper();
    final DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    final JdbcTemplate db = new JdbcTemplate(source);
    final ChatMemoryService memory;
    ChatMemoryServiceTest() {
        db.execute("CREATE TABLE chat_memory_conversation(id VARCHAR(36) PRIMARY KEY,owner_key VARCHAR(64),account VARCHAR(64),title VARCHAR(160),category VARCHAR(32),primary_index VARCHAR(191),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE chat_memory_message(sequence_id BIGINT AUTO_INCREMENT PRIMARY KEY,id VARCHAR(36) UNIQUE,conversation_id VARCHAR(36) REFERENCES chat_memory_conversation(id),role VARCHAR(16),content MEDIUMTEXT,category VARCHAR(32),primary_index VARCHAR(191),status VARCHAR(16),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        memory = new ChatMemoryService(db, new DataSourceTransactionManager(source), json, true);
    }
    static class Client {
        final Map<String, Object> attributes = new ConcurrentHashMap<>();
        final WebSocketSession session = (WebSocketSession) Proxy.newProxyInstance(WebSocketSession.class.getClassLoader(), new Class<?>[]{WebSocketSession.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getAttributes" -> attributes;
                case "getPrincipal" -> null;
                case "getId" -> "test";
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
    tools.jackson.databind.JsonNode open(String account, String key) {
        return json.valueToTree(Map.of("deviceKey", key.repeat(64), "userContext", Map.of("account", account,
            "walletAddress", "0x71C7656EC7ab88b098defB751B7401B5f6d8976F", "level", 2, "isConnected", true)));
    }
    tools.jackson.databind.JsonNode empty() { return json.readTree("{}"); }
    int count(String table) { return db.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> topics(Map<String, Object> result) { return (List<Map<String, Object>>) result.get("conversations"); }

    @Test
    void persistsReloadsAndIsolatesUsersAndDevices() {
        var a = new Client();
        assertTrue(topics(memory.open(a.session, open("alice", "a"))).isEmpty());
        memory.begin(a.session, "錢包餘額怎麼查？");
        assertEquals(1, count("chat_memory_message"), "user question checkpoint exists before inference");
        assertThrows(IllegalArgumentException.class, () -> memory.newConversation(a.session, empty()));
        memory.finish(a.session, "請查看錢包頁面。", "COMPLETE");
        var list = topics(memory.list(a.session, empty()));
        String id = list.get(0).get("id").toString();
        assertEquals("wallet", list.get(0).get("category"));
        memory.logout(a.session);
        assertTrue(a.attributes.isEmpty());
        assertEquals(2, count("chat_memory_message"));
        var again = new Client();
        assertEquals(1, topics(memory.open(again.session, open("alice", "a"))).size());
        assertEquals(3, memory.prompt(again.session, "繼續").size(), "login restores latest history into the session");
        var selected = memory.select(again.session, json.valueToTree(Map.of("conversationId", id)));
        assertEquals(2, ((List<?>) selected.get("messages")).size());
        var prompt = memory.prompt(again.session, "繼續");
        assertEquals("錢包餘額怎麼查？", prompt.get(0).get("content"));
        assertTrue(memory.context(again.session).contains("alice"));
        for (var credentials : List.of(open("bob", "a"), open("alice", "b"))) {
            var other = new Client();
            assertTrue(topics(memory.open(other.session, credentials)).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> memory.select(other.session, json.valueToTree(Map.of("conversationId", id))));
            memory.logout(other.session);
        }
        memory.logout(again.session);
    }

    @Test
    void failedSaveRetainsSessionAndRetryDoesNotDuplicateMessages() {
        var client = new Client(); memory.open(client.session, open("alice", "a"));
        memory.begin(client.session, "請記住我偏好簡短回答");
        db.execute("ALTER TABLE chat_memory_message RENAME TO offline_messages");
        assertThrows(Exception.class, () -> memory.finish(client.session, "好的", "COMPLETE"));
        assertThrows(Exception.class, () -> memory.logout(client.session));
        assertFalse(client.attributes.isEmpty(), "failed transaction must not clear the session");
        db.execute("ALTER TABLE offline_messages RENAME TO chat_memory_message");
        memory.logout(client.session);
        assertTrue(client.attributes.isEmpty());
        assertEquals(2, count("chat_memory_message"));
    }

    @Test
    void pagesHistoryAndBoundsPromptAndStoresPerQuestionIndexes() {
        var client = new Client(); memory.open(client.session, open("alice", "a"));
        for (int i = 0; i < 60; i++) { memory.begin(client.session, "轉帳問題 " + i); memory.finish(client.session, "回覆 " + i, "COMPLETE"); }
        String id = topics(memory.list(client.session, empty())).get(0).get("id").toString();
        var selected = memory.select(client.session, json.valueToTree(Map.of("conversationId", id)));
        assertTrue((Boolean) selected.get("hasMore"));
        var messages = (List<?>) selected.get("messages");
        assertEquals(50, messages.size());
        var first = (ChatMemoryService.Entry) messages.get(0);
        var older = memory.older(client.session, json.valueToTree(Map.of("beforeSequence", first.sequence())));
        assertEquals(50, ((List<?>) older.get("messages")).size());
        assertEquals(21, memory.prompt(client.session, "還有嗎？").size());
        assertEquals(60, db.queryForObject("SELECT COUNT(*) FROM chat_memory_message WHERE role='user' AND category='transfer'", Integer.class));
        assertEquals(1, topics(memory.list(client.session, json.valueToTree(Map.of("category", "transfer", "query", "轉帳問題 59")))).size());
        memory.logout(client.session);
    }

    @Test
    void authenticatedModeDoesNotAcceptClaimedAccountAsIdentity() {
        var secured = new ChatMemoryService(db, new DataSourceTransactionManager(source), json, false);
        var client = new Client();
        assertThrows(IllegalArgumentException.class, () -> secured.open(client.session, open("alice", "a")));
        assertTrue(client.attributes.isEmpty());
    }
}
