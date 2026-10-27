package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class AiTicket extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private String content;

    private String status;

    private String priority;

    private String aiSummary;

    private String aiCategory;
    private String aiPriority;
    private String aiCause;
    private String aiSuggestion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    public String getAiCategory() {
        return aiCategory;
    }

    public void setAiCategory(String aiCategory) {
        this.aiCategory = aiCategory;
    }

    public String getAiPriority() {
        return aiPriority;
    }

    public void setAiPriority(String aiPriority) {
        this.aiPriority = aiPriority;
    }

    public String getAiCause() {
        return aiCause;
    }

    public void setAiCause(String aiCause) {
        this.aiCause = aiCause;
    }

    public String getAiSuggestion() {
        return aiSuggestion;
    }

    public void setAiSuggestion(String aiSuggestion) {
        this.aiSuggestion = aiSuggestion;
    }
}