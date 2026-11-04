package com.tonic.vitavr.space;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Represents an eye's field of view as OpenXR gives it, in radians with left and down negative.
 */
@Value
@Accessors(fluent = true)
public class Fov
{
    double left;
    double right;
    double up;
    double down;
}
