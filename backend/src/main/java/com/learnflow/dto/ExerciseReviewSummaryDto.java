package com.learnflow.dto;

/**
 * 练习回顾统计摘要。
 */
public class ExerciseReviewSummaryDto {

    private int totalRecords;
    private int scoredRecords;
    private Double averageScore;
    private Integer highestScore;
    private Integer latestScore;
    private int masteredCount;
    private int needsReviewCount;

    public int getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    public int getScoredRecords() {
        return scoredRecords;
    }

    public void setScoredRecords(int scoredRecords) {
        this.scoredRecords = scoredRecords;
    }

    public Double getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(Double averageScore) {
        this.averageScore = averageScore;
    }

    public Integer getHighestScore() {
        return highestScore;
    }

    public void setHighestScore(Integer highestScore) {
        this.highestScore = highestScore;
    }

    public Integer getLatestScore() {
        return latestScore;
    }

    public void setLatestScore(Integer latestScore) {
        this.latestScore = latestScore;
    }

    public int getMasteredCount() {
        return masteredCount;
    }

    public void setMasteredCount(int masteredCount) {
        this.masteredCount = masteredCount;
    }

    public int getNeedsReviewCount() {
        return needsReviewCount;
    }

    public void setNeedsReviewCount(int needsReviewCount) {
        this.needsReviewCount = needsReviewCount;
    }
}
