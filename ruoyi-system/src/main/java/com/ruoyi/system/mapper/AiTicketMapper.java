package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.AiTicket;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface AiTicketMapper {

    // 查询工单
    List<AiTicket> selectAiTicketList();

    // 新增工单
    int insertAiTicket(AiTicket ticket);

    // 修改工单
    int updateAiTicket(AiTicket ticket);

    // 根据ID删除工单
    int deleteAiTicketById(@Param("id") Long id);
}