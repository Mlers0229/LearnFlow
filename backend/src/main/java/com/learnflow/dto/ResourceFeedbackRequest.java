package com.learnflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 用户对资源的评分 / 举报请求体。
 *
 * dayId 不在这里传递，而是通过 URL 中的 resourceId 定位资源。
 * userId 由前端根据当前登录用户自行带上（演示环境不做 token 校验）。
 */
public class ResourceFeedbackRequest {

    /**
     * 当前登录用户 ID，可空。
     */
    private Long userId;

    /**
     * 简单评分（1~5），可空。
     */
    @Min(1)
    @Max(5)
    private Integer rating;

    /**
     * 文本评论，可空。
     */
    @Size(max = 4000)
    private String comment;

    /**
     * 是否举报该资源为“无效 / 不相关”。
     */
    private Boolean reportedInvalid;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Boolean getReportedInvalid() {
        return reportedInvalid;
    }

    public void setReportedInvalid(Boolean reportedInvalid) {
        this.reportedInvalid = reportedInvalid;
    }
}



