package nl.hauntedmc.ailex.util.math;

import java.util.Random;

public class Probability {

    private static final Random random = new Random();

    /**
     * Get a random number from a binomial distribution
     * @return the random number
     */
    public static float getBinomial() {
        int n = 10; // Number of trials
        double p = 0.5; // Probability of success
        int successes = 0;

        for (int i = 0; i < n; i++) {
            if (random.nextDouble() < p) {
                successes++;
            }
        }

        return successes;
    }
}
