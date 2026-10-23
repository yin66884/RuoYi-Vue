package com.ruoyi.system.service.impl;

import com.ruoyi.system.domain.AiTicket;
import com.ruoyi.system.mapper.AiTicketMapper;
import com.ruoyi.system.service.IAiTicketService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

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

}
