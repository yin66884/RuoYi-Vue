package com.ruoyi.web.controller.ai;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.system.domain.AiTicket;
import com.ruoyi.system.service.IAiTicketService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.domain.AjaxResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@RequestMapping("/ticket")
public class AiTicketController extends BaseController {

    @Autowired
    private IAiTicketService aiTicketService;

    @GetMapping("/list")
    public TableDataInfo list() {

        startPage();

        List<AiTicket> tickets =
                aiTicketService.selectAiTicketList();

        return getDataTable(tickets);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping
    public AjaxResult add(@RequestBody AiTicket ticket) {

        return toAjax(aiTicketService.insertAiTicket(ticket));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping
    public AjaxResult edit(@RequestBody AiTicket ticket) {

        if (ticket.getId() == null || ticket.getId() <= 0) {
            return AjaxResult.error("工单ID无效");
        }

        if (ticket.getTitle() == null || ticket.getTitle().isBlank()
                || ticket.getContent() == null || ticket.getContent().isBlank()
                || ticket.getStatus() == null || ticket.getPriority() == null) {
            return AjaxResult.error("请填写完整工单信息");
        }

        return toAjax(aiTicketService.updateAiTicket(ticket));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id) {

        if (id == null || id <= 0) {
            return AjaxResult.error("工单ID无效");
        }

        return toAjax(aiTicketService.deleteAiTicketById(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("/{id}/analyze")
    public AjaxResult analyze(@PathVariable("id") Long id) {

        try {
            String summary = aiTicketService.analyzeAiTicket(id);

            return AjaxResult.success(summary);

        } catch (IllegalArgumentException | IllegalStateException e) {
            return AjaxResult.error(e.getMessage());
        }
    }
}


