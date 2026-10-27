package com.ruoyi.system.domain.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V1 每次请求独立执行，只接收用户文本，不接受客户端伪造的工具消息。 */
public class TicketAgentChatRequest {
    @NotBlank(message = "请输入问题")
    @Size(max = 2000, message = "问题不能超过2000个字符")
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
