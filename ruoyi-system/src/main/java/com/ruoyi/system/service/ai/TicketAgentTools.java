package com.ruoyi.system.service.ai;

import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.system.domain.AiTicket;
import com.ruoyi.system.mapper.AiTicketMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 模型仅能选择这里公开的只读工具；白名单和参数校验由 Java 强制执行。 */
@Component
public class TicketAgentTools {
    private static final List<String> STATUSES = List.of("OPEN", "PROCESSING", "CLOSED");
    private static final List<String> PRIORITIES = List.of("HIGH", "NORMAL", "LOW");
    private final AiTicketMapper mapper;

    public TicketAgentTools(AiTicketMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> definitions() {
        return List.of(
            function("query_tickets", "按状态和业务优先级分页查询真实工单。未处理=OPEN，处理中=PROCESSING，已完成=CLOSED。"
                + "priority 指人工设置的业务优先级，不是 aiPriority。未提供筛选条件表示不限。默认第1页、每页10条，最多20条。"
                + "必须根据 total 和 hasMore 说明是否还有未展示记录，不能把一页说成全部。",
                Map.of("status", Map.of("type", "string", "enum", STATUSES),
                       "priority", Map.of("type", "string", "enum", PRIORITIES),
                       "page", Map.of("type", "integer", "minimum", 1, "maximum", 10000),
                       "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 20)), List.of()),
            function("get_ticket_detail", "按工单ID读取原始问题及已有AI分析。需要具体排查建议时，优先读取详情。不存在时返回 found=false。",
                Map.of("id", Map.of("type", "integer", "minimum", 1)), List.of("id"))
        );
    }

    private Map<String, Object> function(String name, String description,
                                          Map<String, Object> properties, List<String> required) {
        return Map.of("type", "function", "function", Map.of("name", name, "description", description,
            "parameters", Map.of("type", "object", "properties", properties,
                                 "required", required, "additionalProperties", false)));
    }

    public JSONObject parseArguments(String text) {
        if (text == null || text.length() > 4000) {
            throw new IllegalArgumentException("工具参数为空或过长");
        }
        try {
            JSONObject args = JSON.parseObject(text);
            if (args == null) {
                throw new IllegalArgumentException("工具参数必须为JSON对象");
            }
            return args;
        } catch (JSONException e) {
            throw new IllegalArgumentException("工具参数必须为合法JSON对象");
        }
    }

    public Map<String, Object> execute(String name, JSONObject args) {
        // 禁止反射调用任意方法，也不接收模型生成的 SQL。
        return switch (name) {
            case "query_tickets" -> queryTickets(args);
            case "get_ticket_detail" -> getTicketDetail(args);
            default -> throw new IllegalArgumentException("不支持的工具：" + name);
        };
    }

    private Map<String, Object> queryTickets(JSONObject args) {
        checkKeys(args, Set.of("status", "priority", "page", "pageSize"));
        String status = enumValue(args, "status", STATUSES);
        String priority = enumValue(args, "priority", PRIORITIES);
        int page = (int) integer(args, "page", 1, 1, 10000);
        int pageSize = (int) integer(args, "pageSize", 10, 1, 20);
        int offset = (page - 1) * pageSize;
        long total = mapper.countTicketsForAgent(status, priority);
        List<Map<String, Object>> items = mapper.selectTicketsForAgent(status, priority, offset, pageSize)
            .stream().map(ticket -> ticketData(ticket, false)).toList();
        return Map.of("total", total, "page", page, "pageSize", pageSize,
                      "hasMore", (long) offset + items.size() < total, "items", items);
    }

    private Map<String, Object> getTicketDetail(JSONObject args) {
        checkKeys(args, Set.of("id"));
        long id = integer(args, "id", null, 1, Long.MAX_VALUE);
        AiTicket ticket = mapper.selectAiTicketById(id);
        return ticket == null ? Map.of("found", false, "id", id)
                              : Map.of("found", true, "ticket", ticketData(ticket, true));
    }

    private void checkKeys(JSONObject args, Set<String> allowed) {
        if (!allowed.containsAll(args.keySet())) {
            throw new IllegalArgumentException("工具包含未定义的参数");
        }
    }

    private String enumValue(JSONObject args, String key, List<String> allowed) {
        if (!args.containsKey(key)) {
            return null;
        }
        Object value = args.get(key);
        if (!(value instanceof String) || !allowed.contains(value)) {
            throw new IllegalArgumentException(key + "只能为" + allowed);
        }
        return (String) value;
    }

    private long integer(JSONObject args, String key, Integer fallback, long min, long max) {
        if (!args.containsKey(key) && fallback != null) {
            return fallback;
        }
        Object value = args.get(key);
        // 不使用 getLongValue：它会将缺失值、字符串或小数隐式转换。
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException(key + "必须是整数");
        }
        long number = ((Number) value).longValue();
        if (number < min || number > max) {
            throw new IllegalArgumentException(key + "超出允许范围");
        }
        return number;
    }

    private Map<String, Object> ticketData(AiTicket ticket, boolean detail) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", ticket.getId());
        data.put("title", clip(ticket.getTitle(), 200));
        data.put("status", ticket.getStatus());
        data.put("priority", ticket.getPriority());
        data.put("aiSummary", clip(ticket.getAiSummary(), 300));
        data.put("aiCategory", clip(ticket.getAiCategory(), 50));
        data.put("aiPriority", ticket.getAiPriority());
        if (detail) {
            data.put("content", clip(ticket.getContent(), 3000));
            data.put("aiCause", clip(ticket.getAiCause(), 1500));
            data.put("aiSuggestion", clip(ticket.getAiSuggestion(), 2500));
        }
        return data;
    }

    private String clip(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "（内容已截断）";
    }
}
