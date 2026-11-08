package com.tonic.vitavr.gestures.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One hand at one moment of a take, in the body frame: seconds from the take's start, metres x right, y up, z
 * forward, and the hand's orientation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Sample
{
    private double t;
    private double x;
    private double y;
    private double z;
    private double qx;
    private double qy;
    private double qz;
    private double qw = 1;
}
