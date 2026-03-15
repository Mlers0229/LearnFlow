package com.learnflow.dto;

import java.time.LocalDate;

public class FeedbackTrendPoint {
    private LocalDate day;
    private Double avgRating;
    private Long feedbackCount;
    private Long invalidReportCount;

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
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


