package com.learnflow.service;

import java.util.List;

/** Pure, deterministic v1 mastery projection. */
public final class MasteryCalculator {

    private MasteryCalculator() {
    }

    public static Result calculate(List<WeightedSignal> signals) {
        double weightedSum = 0;
        double effectiveWeight = 0;
        int sampleCount = 0;
        for (WeightedSignal signal : signals) {
            if (signal == null || signal.weight() <= 0 || signal.value() == null) {
                continue;
            }
            double value = clamp(signal.value(), 0, 1);
            double weight = clamp(signal.weight(), 0, 2);
            weightedSum += value * weight;
            effectiveWeight += weight;
            sampleCount++;
        }
        if (effectiveWeight <= 0) {
            return new Result(0.5, 0, 0, 0);
        }
        double score = weightedSum / effectiveWeight;
        double confidence = Math.min(0.95, 1 - Math.exp(-effectiveWeight / 4.0));
        return new Result(round4(score), round4(confidence), round4(effectiveWeight), sampleCount);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    public record WeightedSignal(Double value, double weight) {
    }

    public record Result(double score, double confidence, double effectiveWeight, int sampleCount) {
    }
}

