package com.ruoyi.system.service.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.system.domain.AiTicketAnalysis;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/** 使用本地 HTTP 模拟模型服务，验证真实请求和响应解析，不消耗 API 额度。 */
class DeepSeekClientTest {
    private HttpServer server;
    private DeepSeekClient client;
    private final LinkedBlockingQueue<Reply> replies = new LinkedBlockingQueue<>();
    private final List<JSONObject> requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requests.add(JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            Reply reply = replies.poll();
            if (reply == null) reply = new Reply(500, "{}", 0);
            try {
                if (reply.delayMs() > 0) Thread.sleep(reply.delayMs());
                byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(reply.status(), body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // 超时测试会主动关闭客户端连接。
            } finally {
                exchange.close();
            }
        });
        server.start();
        client = new DeepSeekClient("test-key", "http://127.0.0.1:" + server.getAddress().getPort(), "test-model");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private record Reply(int status, String body, long delayMs) {
    }

    private void reply(String finishReason, Map<String, Object> message) {
        replies.add(new Reply(200, JSON.toJSONString(Map.of("choices", List.of(
            Map.of("finish_reason", finishReason, "message", message)), "usage", Map.of("total_tokens", 42))), 0));
    }

    @Test
    void preservesToolCallsAndSendsMatchingToolResultWithNullableContent() {
        reply("tool_calls", Map.of("role", "assistant", "tool_calls", List.of(
            Map.of("id", "call-7", "type", "function", "function",
                Map.of("name", "get_ticket_detail", "arguments", "{\"id\":7}")))));
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "查询7号工单"));
        List<Map<String, Object>> definitions = new TicketAgentTools(null).definitions();
        DeepSeekClient.ChatCompletion first = client.chat(messages, definitions, Duration.ofSeconds(2));
        assertEquals("call-7", first.message().getJSONArray("tool_calls").getJSONObject(0).getString("id"));
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", null);
        assistant.put("tool_calls", first.message().get("tool_calls"));
        messages.add(assistant);
        messages.add(Map.of("role", "tool", "tool_call_id", "call-7", "content", "{\"found\":false}"));
        reply("stop", Map.of("role", "assistant", "content", "未找到7号工单"));
        assertEquals("未找到7号工单", client.chat(messages, definitions, Duration.ofSeconds(2)).message().getString("content"));
        JSONObject request = requests.get(1);
        assertEquals("test-model", request.getString("model"));
        assertEquals("disabled", request.getJSONObject("thinking").getString("type"));
        assertFalse(request.containsKey("response_format"));
        JSONObject sentAssistant = request.getJSONArray("messages").getJSONObject(1);
        assertTrue(sentAssistant.containsKey("content"));
        assertNull(sentAssistant.get("content"));
        assertEquals("call-7", request.getJSONArray("messages").getJSONObject(2).getString("tool_call_id"));
        assertEquals(42, first.totalTokens());
    }

    @Test
    void analyzeStillUsesStructuredOutputAndValidatesFields() {
        String analysis = JSON.toJSONString(Map.of("summary", "Redis不可达", "category", "缓存",
            "priority", "HIGH", "cause", "可能未启动", "suggestion", "检查端口"));
        reply("stop", Map.of("role", "assistant", "content", analysis));
        AiTicketAnalysis result = client.analyze("登录失败", "连接6379被拒绝");
        assertEquals("HIGH", result.getPriority());
        assertEquals("缓存", result.getCategory());
        assertEquals("json_object", requests.get(0).getJSONObject("response_format").getString("type"));
    }

    @Test
    void rejectsHttpFailuresMalformedJsonEmptyChoicesAndTruncatedOutput() {
        replies.add(new Reply(429, "{\"secret\":\"private-provider-detail\"}", 0));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> client.chat(List.of(), List.of(), Duration.ofSeconds(2)));
        assertTrue(failure.getMessage().contains("429"));
        assertFalse(failure.getMessage().contains("private-provider-detail"));
        replies.add(new Reply(200, "not-json", 0));
        assertThrows(IllegalStateException.class, () -> client.chat(List.of(), List.of(), Duration.ofSeconds(2)));
        replies.add(new Reply(200, "{\"choices\":[]}", 0));
        assertThrows(IllegalStateException.class, () -> client.chat(List.of(), List.of(), Duration.ofSeconds(2)));
        reply("length", Map.of("role", "assistant", "content", "截断"));
        assertThrows(IllegalStateException.class, () -> client.chat(List.of(), List.of(), Duration.ofSeconds(2)));
    }

    @Test
    void timeoutAndMissingKeyHaveExplicitErrors() {
        replies.add(new Reply(200, "{}", 400));
        IllegalStateException timeout = assertThrows(IllegalStateException.class,
            () -> client.chat(List.of(), List.of(), Duration.ofMillis(50)));
        assertTrue(timeout.getMessage().contains("超时"));
        DeepSeekClient missingKey = new DeepSeekClient("", "http://127.0.0.1", "test-model");
        assertTrue(assertThrows(IllegalStateException.class,
            () -> missingKey.chat(List.of(), List.of(), Duration.ofSeconds(1))).getMessage().contains("DEEPSEEK_API_KEY"));
    }
}
