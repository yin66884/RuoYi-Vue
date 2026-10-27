package com.ruoyi.web.controller.ai;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.system.domain.ai.TicketAgentChatRequest;
import com.ruoyi.system.service.ai.TicketAgentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ticket/agent")
public class TicketAgentController {
    private static final Logger log = LoggerFactory.getLogger(TicketAgentController.class);
    private final TicketAgentService service;

    public TicketAgentController(TicketAgentService service) {
        this.service = service;
    }

    // V1 查询全局工单，统一限定管理员。后续开放普通用户时必须补充数据范围校验。
    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("/chat")
    public AjaxResult chat(@Valid @RequestBody TicketAgentChatRequest request) {
        try {
            return AjaxResult.success(service.chat(request.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("[Agent] chat failed: {}", e.getMessage());
            return AjaxResult.error(e.getMessage());
        }
    }
}
