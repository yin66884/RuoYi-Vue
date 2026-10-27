package com.ruoyi.system.service.impl;

import com.ruoyi.system.domain.AiTicket;
import com.ruoyi.system.mapper.AiTicketMapper;
import com.ruoyi.system.service.IAiTicketService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.ruoyi.system.service.ai.DeepSeekClient;

import com.ruoyi.system.domain.AiTicketAnalysis;

import java.util.List;

@Service
public class AiTicketServiceImpl implements IAiTicketService {

    @Autowired
    private AiTicketMapper aiTicketMapper;

    @Override
    public List<AiTicket> selectAiTicketList() {
        return aiTicketMapper.selectAiTicketList();
    }

    @Override
    public int insertAiTicket(AiTicket ticket) {
        return aiTicketMapper.insertAiTicket(ticket);
    }

    @Override
    public int updateAiTicket(AiTicket ticket) {
        return aiTicketMapper.updateAiTicket(ticket);
    }

    @Override
    public int deleteAiTicketById(Long id) {
        return aiTicketMapper.deleteAiTicketById(id);
    }

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Override
    public String analyzeAiTicket(Long id) {

        // 1. 查询工单
        AiTicket ticket = aiTicketMapper.selectAiTicketById(id);

        if (ticket == null) {
            throw new IllegalArgumentException("工单不存在");
        }

        // 2. 一次调用大模型，生成五项分析结果
        AiTicketAnalysis analysis = deepSeekClient.analyze(
                ticket.getTitle(),
                ticket.getContent()
        );

        // 3. 一次性保存到MySQL
        int rows = aiTicketMapper.updateAiAnalysis(id, analysis);

        if (rows != 1) {
            throw new IllegalStateException("AI分析结果保存失败");
        }

        // 4. 保持原Controller的返回类型兼容
        return analysis.getSummary();
    }
}
