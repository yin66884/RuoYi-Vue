package com.ruoyi.system.service;

import com.ruoyi.system.domain.AiTicket;
import java.util.List;

public interface IAiTicketService {

    List<AiTicket> selectAiTicketList();

    int insertAiTicket(AiTicket ticket);

    // 修改工单
    int updateAiTicket(AiTicket ticket);

    // 删除工单
    int deleteAiTicketById(Long id);

    String analyzeAiTicket(Long id);
}