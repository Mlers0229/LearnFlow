package com.learnflow.dto;

/**
 * 学习计划完成情况统计 DTO。
 *
 * 用于前端展示进度条和数字，例如：
 * - completedDays / totalDays
 * - completionRate 百分比
 */
public class PlanProgressDto {

    private int totalDays;
    private int completedDays;
    private int inProgressDays;
    private int notStartedDays;
    private int delayedDays;

    /**
     * 完成率（0~100 的整数），在服务端计算好，前端直接展示即可。
     */
    private int completionRate;

    public int getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(int totalDays) {
        this.totalDays = totalDays;
    }

    public int getCompletedDays() {
        return completedDays;
    }

    public void setCompletedDays(int completedDays) {
        this.completedDays = completedDays;
    }

    public int getInProgressDays() {
        return inProgressDays;
    }

    public void setInProgressDays(int inProgressDays) {
        this.inProgressDays = inProgressDays;
    }

    public int getNotStartedDays() {
        return notStartedDays;
    }

    public void setNotStartedDays(int notStartedDays) {
        this.notStartedDays = notStartedDays;
    }

    public int getDelayedDays() {
        return delayedDays;
    }

    public void setDelayedDays(int delayedDays) {
        this.delayedDays = delayedDays;
    }

    public int getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(int completionRate) {
        this.completionRate = completionRate;
    }
}



