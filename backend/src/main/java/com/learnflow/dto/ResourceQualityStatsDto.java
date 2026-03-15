package com.learnflow.dto;

/**
 * 单条资源的质量统计信息，用于管理端「资源质量看板」。
 *
 * 字段尽量简单，方便在论文里直接解释：
 * - avgRating：平均评分（1~5）；没有评分时为 null；
 * - feedbackCount：总反馈次数；
 * - invalidReportCount：被举报为“无效 / 不相关”的次数。
 */
public class ResourceQualityStatsDto {

    private Long resourceId;

    private Double avgRating;

    private Long feedbackCount;

    private Long invalidReportCount;

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public Double getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(Double avgRating) {
        this.avgRating = avgRating;
    }

    public Long getFeedbackCount() {
        return feedbackCount;
    }

    public void setFeedbackCount(Long feedbackCount) {
        this.feedbackCount = feedbackCount;
    }

    public Long getInvalidReportCount() {
        return invalidReportCount;
    }

    public void setInvalidReportCount(Long invalidReportCount) {
        this.invalidReportCount = invalidReportCount;
    }
}



