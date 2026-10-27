package com.ruoyi.system.service.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.system.domain.AiTicket;
import com.ruoyi.system.domain.ai.TicketAgentChatResponse;
import com.ruoyi.system.mapper.AiTicketMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class TicketAgentV1Test {
    private AiTicket ticket() {
        AiTicket ticket = new AiTicket();
        ticket.setId(7L);
        ticket.setTitle("Redis连接失败");
        ticket.setStatus("OPEN");
        ticket.setPriority("HIGH");
        ticket.setContent("应用日志提示连接6379端口被拒绝");
        ticket.setAiSuggestion("检查服务是否启动");
        return ticket;
    }

    private TicketAgentTools tools(AtomicInteger reads) {
        AiTicketMapper mapper = (AiTicketMapper) Proxy.newProxyInstance(
            AiTicketMapper.class.getClassLoader(), new Class<?>[]{AiTicketMapper.class}, (proxy, method, args) -> {
                reads.incrementAndGet();
                return switch (method.getName()) {
                    case "countTicketsForAgent" -> 23L;
                    case "selectTicketsForAgent" -> {
                        assertEquals("OPEN", args[0]);
                        assertEquals("HIGH", args[1]);
                        yield List.of(ticket());
                    }
                    case "selectAiTicketById" -> Long.valueOf(7).equals(args[0]) ? ticket() : null;
                    default -> throw new AssertionError("只允许查询，实际方法=" + method.getName());
                };
            });
        return new TicketAgentTools(mapper);
    }

    private DeepSeekClient fakeClient(Function<List<Map<String, Object>>, JSONObject> responder) {
        return new DeepSeekClient("test-key", "http://localhost", "test-model") {
            @Override
            public ChatCompletion chat(List<Map<String, Object>> messages,
                                        List<Map<String, Object>> definitions, Duration timeout) {
                assertEquals(2, definitions.size());
                assertFalse(timeout.isNegative() || timeout.isZero());
                return new ChatCompletion(responder.apply(messages), 10);
            }
        };
    }

    private JSONObject call(String id, String name, String args) {
        return JSONObject.of("id", id, "type", "function",
            "function", JSONObject.of("name", name, "arguments", args));
    }

    private JSONObject calls(JSONObject... calls) {
        JSONObject message = new JSONObject();
        message.put("role", "assistant");
        message.put("content", null);
        message.put("tool_calls", new JSONArray(List.of(calls)));
        return message;
    }

    private JSONObject answer(String text) {
        return JSONObject.of("role", "assistant", "content", text);
    }

    @Test
    void queryReturnsTotalPaginationAndBusinessPriority() {
        AtomicInteger reads = new AtomicInteger();
        Map<String, Object> result = tools(reads).execute("query_tickets",
            JSON.parseObject("{\"status\":\"OPEN\",\"priority\":\"HIGH\",\"page\":2,\"pageSize\":10}"));
        assertEquals(23L, result.get("total"));
        assertEquals(2, result.get("page"));
        assertEquals(true, result.get("hasMore"));
        assertEquals(2, reads.get());
    }

    @Test
    void rejectsInvalidParametersAndUnknownToolsBeforeDatabaseAccess() {
        AtomicInteger reads = new AtomicInteger();
        TicketAgentTools tools = tools(reads);
        for (String args : List.of("{\"status\":\"ALL\"}", "{\"status\":null}", "{\"priority\":\"URGENT\"}",
                "{\"page\":0}", "{\"pageSize\":21}", "{\"page\":1.5}", "{\"page\":\"1\"}", "{\"sql\":\"DROP TABLE ai_ticket\"}")) {
            assertThrows(IllegalArgumentException.class, () -> tools.execute("query_tickets", JSON.parseObject(args)));
        }
        assertThrows(IllegalArgumentException.class, () -> tools.execute("delete_ticket", new JSONObject()));
        assertThrows(IllegalArgumentException.class, () -> tools.execute("get_ticket_detail", JSONObject.of("id", -1)));
        assertThrows(IllegalArgumentException.class, () -> tools.execute("get_ticket_detail", new JSONObject()));
        assertThrows(IllegalArgumentException.class, () -> tools.parseArguments("not-json"));
        assertThrows(IllegalArgumentException.class, () -> tools.parseArguments("[]"));
        assertEquals(0, reads.get());
    }

    @Test
    void missingTicketIsExplicitAndDetailsIncludeEvidence() {
        TicketAgentTools tools = tools(new AtomicInteger());
        assertEquals(false, tools.execute("get_ticket_detail", JSONObject.of("id", 999)).get("found"));
        Map<?, ?> detail = (Map<?, ?>) tools.execute("get_ticket_detail", JSONObject.of("id", 7)).get("ticket");
        assertEquals("应用日志提示连接6379端口被拒绝", detail.get("content"));
        assertEquals("检查服务是否启动", detail.get("aiSuggestion"));
    }

    @Test
    void executesMultipleCallsAndPreservesAssistantAndMatchingToolIds() {
        AtomicInteger rounds = new AtomicInteger();
        DeepSeekClient client = fakeClient(messages -> {
            if (rounds.getAndIncrement() == 0) {
                return calls(call("list-1", "query_tickets", "{\"status\":\"OPEN\",\"priority\":\"HIGH\"}"),
                    call("detail-1", "get_ticket_detail", "{\"id\":7}"));
            }
            assertEquals(5, messages.size());
            assertEquals("assistant", messages.get(2).get("role"));
            assertTrue(messages.get(2).containsKey("content"));
            assertNull(messages.get(2).get("content"));
            assertEquals("list-1", messages.get(3).get("tool_call_id"));
            assertEquals("detail-1", messages.get(4).get("tool_call_id"));
            assertEquals(23, JSON.parseObject((String) messages.get(3).get("content")).getIntValue("total"));
            return answer("工单#7记录了端口连接被拒绝，建议先检查Redis服务。");
        });
        TicketAgentChatResponse response = new TicketAgentService(client, tools(new AtomicInteger())).chat("查工单");
        assertEquals("completed", response.stopReason());
        assertEquals(2, response.toolCalls().size());
        assertEquals(20, response.totalTokens());
        assertTrue(response.toolCalls().stream().allMatch(TicketAgentChatResponse.ToolExecution::success));
    }

    @Test
    void parameterErrorIsReturnedToModelAndCanBeCorrected() {
        AtomicInteger rounds = new AtomicInteger();
        AtomicInteger reads = new AtomicInteger();
        DeepSeekClient client = fakeClient(messages -> switch (rounds.getAndIncrement()) {
            case 0 -> calls(call("invalid", "get_ticket_detail", "{\"id\":-1}"));
            case 1 -> {
                assertTrue(JSON.parseObject((String) messages.get(3).get("content")).containsKey("error"));
                yield calls(call("fixed", "get_ticket_detail", "{\"id\":7}"));
            }
            default -> answer("已查询工单#7");
        });
        TicketAgentChatResponse response = new TicketAgentService(client, tools(reads)).chat("查详情");
        assertFalse(response.toolCalls().get(0).success());
        assertTrue(response.toolCalls().get(1).success());
        assertEquals(1, reads.get());
    }

    @Test
    void databaseFailureIsSanitized() {
        AiTicketMapper mapper = (AiTicketMapper) Proxy.newProxyInstance(AiTicketMapper.class.getClassLoader(),
            new Class<?>[]{AiTicketMapper.class}, (proxy, method, args) -> {
                throw new IllegalStateException("jdbc:mysql://private-host secret-password");
            });
        AtomicInteger rounds = new AtomicInteger();
        DeepSeekClient client = fakeClient(messages -> rounds.getAndIncrement() == 0
            ? calls(call("db-error", "get_ticket_detail", "{\"id\":7}")) : answer("查询失败，请重试"));
        TicketAgentChatResponse response = new TicketAgentService(client, new TicketAgentTools(mapper)).chat("查详情");
        String result = JSON.toJSONString(response.toolCalls().get(0).result());
        assertFalse(result.contains("private-host"));
        assertFalse(result.contains("secret-password"));
        assertFalse(response.toolCalls().get(0).success());
    }

    @Test
    void stopsAtRoundAndToolBudgets() {
        AtomicInteger rounds = new AtomicInteger();
        TicketAgentChatResponse roundLimit = new TicketAgentService(fakeClient(messages ->
            calls(call("r" + rounds.incrementAndGet(), "get_ticket_detail", "{\"id\":7}"))), tools(new AtomicInteger())).chat("查详情");
        assertEquals("round_limit", roundLimit.stopReason());
        assertEquals(5, roundLimit.toolCalls().size());

        List<JSONObject> excessive = new ArrayList<>();
        for (int i = 0; i < 9; i++) excessive.add(call("c" + i, "get_ticket_detail", "{\"id\":7}"));
        AtomicInteger reads = new AtomicInteger();
        TicketAgentChatResponse toolLimit = new TicketAgentService(fakeClient(messages -> calls(excessive.toArray(JSONObject[]::new))),
            tools(reads)).chat("查详情");
        assertEquals("tool_limit", toolLimit.stopReason());
        assertEquals(0, reads.get());
    }

    @Test
    void requestsDoNotShareMessagesAndInvalidInputDoesNotCallModel() {
        List<Integer> initialSizes = new ArrayList<>();
        TicketAgentService service = new TicketAgentService(fakeClient(messages -> {
            initialSizes.add(messages.size());
            return answer("请提供工单ID");
        }), tools(new AtomicInteger()));
        service.chat("第一次");
        service.chat("第二次");
        assertEquals(List.of(2, 2), initialSizes);
        assertThrows(IllegalArgumentException.class, () -> service.chat(" "));
        assertThrows(IllegalArgumentException.class, () -> service.chat("a".repeat(2001)));
        assertEquals(2, initialSizes.size());
    }

    @Test
    void mapperXmlUsesSharedFiltersBoundPaginationAndCompleteDetails() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/system/AiTicketMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String namespace = AiTicketMapper.class.getName() + ".";
        Map<String, Object> params = Map.of("status", "OPEN", "priority", "HIGH", "offset", 10, "pageSize", 10);
        BoundSql sql = configuration.getMappedStatement(namespace + "selectTicketsForAgent").getBoundSql(params);
        assertEquals(List.of("status", "priority", "pageSize", "offset"),
            sql.getParameterMappings().stream().map(mapping -> mapping.getProperty()).toList());
        assertTrue(sql.getSql().contains("LIMIT ? OFFSET ?"));
        BoundSql count = configuration.getMappedStatement(namespace + "countTicketsForAgent").getBoundSql(params);
        assertEquals(2, count.getParameterMappings().size());
        String detail = configuration.getMappedStatement(namespace + "selectAiTicketById").getBoundSql(Map.of("id", 7)).getSql();
        assertTrue(detail.contains("ai_suggestion"));
        assertTrue(detail.contains("ai_cause"));
    }
}
