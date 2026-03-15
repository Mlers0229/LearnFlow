package com.learnflow.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 练习回顾列表响应。
 */
public class ExerciseRecordListResponse {

    private ExerciseReviewSummaryDto summary = new ExerciseReviewSummaryDto();

    private List<ExerciseRecordItemDto> items = new ArrayList<>();

    public ExerciseReviewSummaryDto getSummary() {
        return summary;
    }

    public void setSummary(ExerciseReviewSummaryDto summary) {
        this.summary = summary;
    }

    public List<ExerciseRecordItemDto> getItems() {
        return items;
    }

    public void setItems(List<ExerciseRecordItemDto> items) {
        this.items = items;
    }
}
