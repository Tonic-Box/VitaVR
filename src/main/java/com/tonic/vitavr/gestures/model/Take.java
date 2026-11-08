package com.tonic.vitavr.gestures.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * One recorded making of a gesture: each hand's samples, empty for a hand that took no part.
 */
@Data
public class Take
{
    private List<Sample> left = new ArrayList<>();
    private List<Sample> right = new ArrayList<>();

    /**
     * @param hand 0 for left, 1 for right.
     * @return That hand's samples.
     */
    public List<Sample> hand(int hand)
    {
        return hand == 0 ? left : right;
    }

    /**
     * @return The take's length in seconds.
     */
    public double seconds()
    {
        return Math.max(last(left), last(right));
    }

    private static double last(List<Sample> samples)
    {
        return samples.isEmpty() ? 0 : samples.get(samples.size() - 1).getT();
    }
}
