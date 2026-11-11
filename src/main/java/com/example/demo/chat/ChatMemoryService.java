package com.example.demo.chat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Per-WebSocket short-term state. Database writes are atomic and idempotent by message UUID. */
@Service
public class ChatMemoryService {
    private static final String KEY = ChatMemoryService.class.getName();
    private static final Set<String> CATEGORIES = Set.of("general", "account", "wallet", "transfer", "blockchain", "preference");
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final boolean allowDemo;

    public ChatMemoryService(JdbcTemplate db, PlatformTransactionManager manager, ObjectMapper json,
            @Value("${chat.memory.allow-demo:false}") boolean allowDemo) {
        this.db = db; this.tx = new TransactionTemplate(manager); this.json = json; this.allowDemo = allowDemo;
    }

    public record Entry(String id, String sequence, String role, String content, String category, String primaryIndex, String status, String createdAt) { }
    private static class State {
        String owner, account, conversationId = UUID.randomUUID().toString();
        String title = "", category = "general", primaryIndex = "";
        Map<String, Object> context;
        final List<Entry> entries = new ArrayList<>(), pending = new ArrayList<>();
        boolean generating;
    }

    private State state(WebSocketSession socket) {
        Object value = socket.getAttributes().get(KEY);
        if (!(value instanceof State s)) throw new IllegalArgumentException("請先建立聊天記憶連線。");
        return s;
    }
    private String text(JsonNode node, String field, int limit, boolean required) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            if (required) throw new IllegalArgumentException("缺少欄位：" + field);
            return "";
        }
        if (!value.isTextual()) throw new IllegalArgumentException("欄位格式錯誤：" + field);
        String result = value.asText().trim();
        if (result.length() > limit || (required && result.isEmpty())) throw new IllegalArgumentException("欄位長度錯誤：" + field);
        return result;
    }

    public Map<String, Object> open(WebSocketSession socket, JsonNode input) {
        if (socket.getAttributes().containsKey(KEY)) throw new IllegalArgumentException("此連線已登入，請重新連線。");
        var node = input.path("userContext");
        String account = text(node, "account", 64, true);
        String address = text(node, "walletAddress", 42, true);
        if (!address.matches("0x[0-9a-fA-F]{40}")) throw new IllegalArgumentException("錢包地址格式錯誤。");
        var level = node.path("level");
        if (!level.isNull() && !level.isMissingNode() && (!level.isIntegralNumber() || !level.canConvertToInt()))
            throw new IllegalArgumentException("帳號等級格式錯誤。");
        if (!node.path("isConnected").isBoolean() || !node.path("isConnected").asBoolean())
            throw new IllegalArgumentException("請先登入。");
        String identity;
        if (socket.getPrincipal() != null) {
            identity = "principal:" + socket.getPrincipal().getName();
        } else {
            if (!allowDemo) throw new IllegalArgumentException("需要後端登入驗證，無法讀取私人對話。");
            String key = text(input, "deviceKey", 64, true);
            if (!key.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("瀏覽器記憶識別碼格式錯誤。");
            identity = "local-demo:" + account + ":" + key;
        }
        State s = new State();
        s.owner = digest(identity); s.account = account;
        s.context = new LinkedHashMap<>();
        s.context.put("account", account); s.context.put("walletAddress", address);
        s.context.put("level", level.isIntegralNumber() ? level.asInt() : null);
        s.context.put("isConnected", true);
        // Check DB availability before installing the new session state.
        var history = list(s, input);
        socket.getAttributes().put(KEY, s);
        Map<String, Object> result = new LinkedHashMap<>(history);
        result.put("identityMode", socket.getPrincipal() == null ? "local-demo" : "principal");
        result.put("conversationId", s.conversationId);
        var topics = (List<?>) history.get("conversations");
        if (!topics.isEmpty()) {
            try {
                String latestId = ((Map<?, ?>) topics.get(0)).get("id").toString();
                result.put("current", select(socket, json.valueToTree(Map.of("conversationId", latestId))));
            } catch (RuntimeException e) { socket.getAttributes().remove(KEY); throw e; }
        }
        return result;
    }
    private String digest(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public Map<String, Object> list(WebSocketSession socket, JsonNode input) { return list(state(socket), input); }
    private Map<String, Object> list(State s, JsonNode input) {
        int offset = input.path("offset").asInt(0);
        if (offset < 0 || offset > 100000) throw new IllegalArgumentException("分頁範圍錯誤。");
        String category = text(input, "category", 32, false);
        String query = text(input, "query", 80, false).toLowerCase(Locale.ROOT);
        if (!category.isEmpty() && !CATEGORIES.contains(category)) throw new IllegalArgumentException("分類不存在。");
        var rows = db.query("SELECT c.id,c.title,c.category,c.primary_index,c.updated_at FROM chat_memory_conversation c "
                + "WHERE c.owner_key=? AND (?='' OR c.category=? OR EXISTS(SELECT 1 FROM chat_memory_message m WHERE m.conversation_id=c.id AND m.role='user' AND m.category=?)) "
                + "AND (LOCATE(?,LOWER(CONCAT(c.title,' ',c.primary_index)))>0 OR EXISTS(SELECT 1 FROM chat_memory_message m WHERE m.conversation_id=c.id AND m.role='user' AND LOCATE(?,LOWER(m.primary_index))>0)) "
                + "ORDER BY c.updated_at DESC,c.id DESC LIMIT 51 OFFSET ?", (rs, n) -> Map.of(
                    "id", rs.getString("id"), "title", rs.getString("title"), "category", rs.getString("category"),
                    "primaryIndex", rs.getString("primary_index"), "updatedAt", rs.getTimestamp("updated_at").toInstant().toString()),
                s.owner, category, category, category, query, query, offset);
        return Map.of("conversations", rows.subList(0, Math.min(50, rows.size())), "hasMore", rows.size() > 50, "offset", offset);
    }

    public Map<String, Object> select(WebSocketSession socket, JsonNode input) {
        State s = state(socket);
        synchronized (s) {
            idle(s); checkpoint(s);
            String id = text(input, "conversationId", 36, true);
            var rows = db.query("SELECT title,category,primary_index FROM chat_memory_conversation WHERE id=? AND owner_key=?",
                    (rs, n) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3)), id, s.owner);
            if (rows.isEmpty()) throw new IllegalArgumentException("找不到此帳號的對話。");
            var entries = readEntries(id, null, 51);
            s.conversationId = id; s.title = rows.get(0).get(0); s.category = rows.get(0).get(1); s.primaryIndex = rows.get(0).get(2);
            s.entries.clear(); s.entries.addAll(entries);
            trim(s);
            return page(s, entries);
        }
    }
    public Map<String, Object> older(WebSocketSession socket, JsonNode input) {
        State s = state(socket);
        synchronized (s) {
            String before = text(input, "beforeSequence", 20, true);
            if (!before.matches("[0-9]{1,19}")) throw new IllegalArgumentException("訊息分頁格式錯誤。");
            long sequence;
            try { sequence = Long.parseLong(before); } catch (Exception e) { throw new IllegalArgumentException("訊息分頁格式錯誤。"); }
            return page(s, readEntries(s.conversationId, sequence, 51));
        }
    }
    private List<Entry> readEntries(String id, Long before, int limit) {
        List<Entry> result = db.query("SELECT id,sequence_id,role,content,category,primary_index,status,created_at FROM chat_memory_message "
                + "WHERE conversation_id=? AND sequence_id<? ORDER BY sequence_id DESC LIMIT ?", (rs, n) -> new Entry(
                    rs.getString("id"), rs.getString("sequence_id"), rs.getString("role"), rs.getString("content"),
                    rs.getString("category"), rs.getString("primary_index"), rs.getString("status"), rs.getTimestamp("created_at").toInstant().toString()), id, before == null ? Long.MAX_VALUE : before, limit);
        Collections.reverse(result); return result;
    }
    private Map<String, Object> page(State s, List<Entry> entries) {
        var visible = entries.subList(Math.max(0, entries.size() - 50), entries.size());
        boolean more = !visible.isEmpty() && Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM chat_memory_message WHERE conversation_id=? AND sequence_id<?)",
                Boolean.class, s.conversationId, Long.parseLong(visible.get(0).sequence())));
        return Map.of("conversationId", s.conversationId, "title", s.title, "category", s.category,
                "primaryIndex", s.primaryIndex, "messages", visible, "hasMore", more);
    }

    public Map<String, Object> newConversation(WebSocketSession socket, JsonNode input) {
        State s = state(socket);
        synchronized (s) {
            idle(s); checkpoint(s);
            String title = text(input, "title", 160, false), category = text(input, "category", 32, false);
            String index = text(input, "primaryIndex", 191, false);
            if (!category.isEmpty() && !CATEGORIES.contains(category)) throw new IllegalArgumentException("分類不存在。");
            s.conversationId = UUID.randomUUID().toString(); s.title = title; s.primaryIndex = index;
            s.category = category.isEmpty() ? "general" : category; s.entries.clear();
            return Map.of("conversationId", s.conversationId, "messages", List.of(), "title", title, "category", s.category, "primaryIndex", index, "hasMore", false);
        }
    }

    public List<Map<String, String>> prompt(WebSocketSession socket, String question) {
        State s = state(socket);
        synchronized (s) {
            idle(s);
            List<Map<String, String>> history = new ArrayList<>();
            int chars = question.length();
            // Recent complete entries only; bounded by both count and characters.
            for (int i = s.entries.size() - 1; i >= 0 && history.size() < 20; i--) {
                var e = s.entries.get(i);
                if (!e.status().equals("COMPLETE")) continue;
                if (chars + e.content().length() > 48000) break;
                history.add(0, Map.of("role", e.role(), "content", e.content())); chars += e.content().length();
            }
            history.add(Map.of("role", "user", "content", question));
            return history;
        }
    }
    public String context(WebSocketSession socket) { return json.writeValueAsString(state(socket).context); }
    public void begin(WebSocketSession socket, String question) {
        State s = state(socket);
        synchronized (s) {
            idle(s);
            if (s.title.isEmpty()) s.title = clip(question, 80);
            if (s.primaryIndex.isEmpty()) s.primaryIndex = clip(question.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT), 120);
            if (s.category.equals("general") && s.entries.isEmpty()) s.category = classify(question);
            Entry e = entry("user", question, classify(question), clip(question.replaceAll("\\s+", " "), 120), "COMPLETE"); s.pending.add(e); s.entries.add(e);
            checkpoint(s); s.generating = true;
        }
    }
    public void finish(WebSocketSession socket, String reply, String status) {
        State s = state(socket);
        synchronized (s) {
            if (s.generating) {
                Entry question = s.entries.get(s.entries.size() - 1);
                Entry e = entry("assistant", reply, question.category(), question.primaryIndex(), status); s.pending.add(e); s.entries.add(e); s.generating = false;
            }
            checkpoint(s);
            trim(s);
        }
    }
    private Entry entry(String role, String content, String category, String index, String status) {
        return new Entry(UUID.randomUUID().toString(), "", role, content, category, index, status, Instant.now().toString());
    }
    private void idle(State s) { if (s.generating) throw new IllegalArgumentException("請等待回覆完成後再切換對話。"); }
    private void trim(State s) {
        int chars = s.entries.stream().mapToInt(e -> e.content().length()).sum();
        while (s.entries.size() > 1 && (s.entries.size() > 200 || chars > 100000)) chars -= s.entries.remove(0).content().length();
    }
    private String clip(String value, int n) { return value.substring(0, Math.min(n, value.length())); }
    private String classify(String q) {
        if (q.matches("(?s).*(偏好|喜歡|習慣|preference).*")) return "preference";
        if (q.matches("(?s).*(轉帳|轉賬|transfer).*")) return "transfer";
        if (q.matches("(?s).*(帳號|帳戶|登入|account).*")) return "account";
        if (q.matches("(?s).*(錢包|餘額|wallet).*")) return "wallet";
        if (q.matches("(?s).*(區塊|節點|合約|blockchain).*")) return "blockchain";
        return "general";
    }
    private void checkpoint(State s) {
        if (s.pending.isEmpty()) return;
        tx.executeWithoutResult(transaction -> {
            db.update("INSERT INTO chat_memory_conversation(id,owner_key,account,title,category,primary_index) VALUES (?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE id=id", s.conversationId, s.owner, s.account, s.title, s.category, s.primaryIndex);
            String owner = db.queryForObject("SELECT owner_key FROM chat_memory_conversation WHERE id=? FOR UPDATE", String.class, s.conversationId);
            if (!s.owner.equals(owner)) throw new IllegalArgumentException("對話不屬於目前使用者。");
            for (Entry e : s.pending) db.update("INSERT INTO chat_memory_message(id,conversation_id,role,content,category,primary_index,status,created_at) VALUES (?,?,?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE id=id", e.id(), s.conversationId, e.role(), e.content(), e.category(), e.primaryIndex(), e.status(), java.sql.Timestamp.from(Instant.parse(e.createdAt())));
            db.update("UPDATE chat_memory_conversation SET updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND owner_key=?", s.conversationId, s.owner);
        });
        s.pending.clear();
    }
    public void logout(WebSocketSession socket) {
        State s = state(socket);
        synchronized (s) { idle(s); checkpoint(s); socket.getAttributes().remove(KEY); s.entries.clear(); s.context.clear(); }
    }
    public void disconnected(WebSocketSession socket) {
        if (!socket.getAttributes().containsKey(KEY)) return;
        logout(socket);
    }
}
