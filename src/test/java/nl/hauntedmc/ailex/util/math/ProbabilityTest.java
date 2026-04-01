package nl.hauntedmc.ailex.util.math;

import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbabilityTest {

    @RepeatedTest(10)
    void binomialSampleShouldStayWithinConfiguredTrialRange() {
        float sample = Probability.getBinomial();
        assertTrue(sample >= 0.0f);
        assertTrue(sample <= 10.0f);
    }
}
