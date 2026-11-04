package com.tonic.vitavr.xr;

import org.lwjgl.openxr.XR10;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Turns failed OpenXR results into exceptions named by the runtime.
 */
final class XrChecks
{
    private XrChecks()
    {
    }

    /**
     * Checks an OpenXR result.
     *
     * @param result The result the call returned.
     * @param instance The instance used, or null before there is one.
     * @param what The action, for the message.
     * @return The result, if it did not fail.
     * @throws IllegalStateException if the result is a failure
     */
    static int check(int result, XrInstance instance, String what)
    {
        if (result >= 0)
        {
            return result;
        }
        throw new IllegalStateException(what + " failed: " + name(result, instance));
    }

    private static String name(int result, XrInstance instance)
    {
        if (instance == null)
        {
            return "XrResult " + result;
        }
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            ByteBuffer text = stack.calloc(XR10.XR_MAX_RESULT_STRING_SIZE);
            XR10.xrResultToString(instance, result, text);
            return MemoryUtil.memUTF8Safe(text) + " (" + result + ")";
        }
    }
}
