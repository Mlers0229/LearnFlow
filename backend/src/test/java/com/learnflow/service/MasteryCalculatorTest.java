package com.learnflow.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MasteryCalculatorTest {

    @Test
    void calculatesExplainableWeightedAverageAndLowSampleConfidence() {
        MasteryCalculator.Result result = MasteryCalculator.calculate(List.of(
                new MasteryCalculator.WeightedSignal(0.9, 1.0),
                new MasteryCalculator.WeightedSignal(0.25, 0.2),
                new MasteryCalculator.WeightedSignal(null, 0)
        ));

        assertThat(result.score()).isEqualTo(0.7917);
        assertThat(result.effectiveWeight()).isEqualTo(1.2);
        assertThat(result.sampleCount()).isEqualTo(2);
        assertThat(result.confidence()).isEqualTo(0.2592);
        assertThat(result.confidence()).isLessThan(0.5);
    }

    @Test
    void ignoresZeroWeightActivityAndCapsConfidence() {
        List<MasteryCalculator.WeightedSignal> signals = new java.util.ArrayList<>();
        signals.add(new MasteryCalculator.WeightedSignal(1.0, 0));
        for (int index = 0; index < 100; index++) {
            signals.add(new MasteryCalculator.WeightedSignal(1.0, 1.0));
        }

        MasteryCalculator.Result result = MasteryCalculator.calculate(signals);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.sampleCount()).isEqualTo(100);
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    @Test
    void keepsNeutralScoreWhenNoAssessmentEvidenceExists() {
        MasteryCalculator.Result result = MasteryCalculator.calculate(List.of(
                new MasteryCalculator.WeightedSignal(null, 0)
        ));

        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.confidence()).isZero();
        assertThat(result.sampleCount()).isZero();
    }
}

