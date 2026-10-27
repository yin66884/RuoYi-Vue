package com.ruoyi.system.domain.ai;

import java.util.List;
import java.util.Map;

/** 返回实际执行记录，便于页面核对数据来源；不包含模型思维链。 */
public record TicketAgentChatResponse(String requestId, String answer, String stopReason,
                                      long elapsedMs, int totalTokens, List<ToolExecution> toolCalls) {
    public record ToolExecution(String toolCallId, String name, Map<String, Object> arguments,
                                Map<String, Object> result, boolean success, long elapsedMs) {
    }
}
