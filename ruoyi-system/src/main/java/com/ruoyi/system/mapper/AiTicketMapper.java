package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.AiTicket;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import com.ruoyi.system.domain.AiTicketAnalysis;


public interface AiTicketMapper {

    // 查询工单
    List<AiTicket> selectAiTicketList();

    // 新增工单
    int insertAiTicket(AiTicket ticket);

    // 修改工单
    int updateAiTicket(AiTicket ticket);

    // 根据ID删除工单
    int deleteAiTicketById(@Param("id") Long id);

    AiTicket selectAiTicketById(@Param("id") Long id);

    int updateAiSummary(
            @Param("id") Long id,
            @Param("aiSummary") String aiSummary
    );

    // 保存AI智能分析结果
    int updateAiAnalysis(
            @Param("id") Long id,
            @Param("analysis") AiTicketAnalysis analysis
    );

    List<AiTicket> selectTicketsForAgent(
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    long countTicketsForAgent(@Param("status") String status,
                              @Param("priority") String priority);

}
