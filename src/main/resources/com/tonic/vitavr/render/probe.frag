#version 330

// Walks the pointer ray across the eye's depth a pixel at a time and gives the distance along it to the first surface, or -1
uniform sampler2D depths;
uniform vec4 start;
uniform vec4 end;
uniform vec2 span;
uniform vec2 size;
uniform float depthScale;
out float found;

const int MOST_STEPS = 4096;
const int REFINE_STEPS = 6;
const float THICKNESS = 64.0;

float along(float u)
{
    return u * start.w / ((1.0 - u) * end.w + u * start.w);
}

bool behind(float u)
{
    vec4 clip = mix(start, end, along(u));
    float stored = texelFetch(depths, ivec2(clamp((clip.xy / clip.w * 0.5 + 0.5) * size, vec2(0.0), size - 1.0)), 0).r;
    float ray = clip.z / clip.w;
    if (stored <= 0.0 || ray > stored)
    {
        return false;
    }
    float surfaceAway = depthScale / stored;
    return depthScale / ray - surfaceAway <= max(THICKNESS, surfaceAway * 0.05);
}

void main()
{
    vec2 from = (start.xy / start.w * 0.5 + 0.5) * size;
    vec2 to = (end.xy / end.w * 0.5 + 0.5) * size;
    int steps = int(clamp(ceil(length(to - from)), 1.0, float(MOST_STEPS)));
    for (int step = 1; step <= steps; step++)
    {
        float u = float(step) / float(steps);
        if (!behind(u))
        {
            continue;
        }

        // Narrow the hit down within the last pixel
        float low = float(step - 1) / float(steps);
        float high = u;
        for (int refine = 0; refine < REFINE_STEPS; refine++)
        {
            float middle = (low + high) * 0.5;
            if (behind(middle))
            {
                high = middle;
            }
            else
            {
                low = middle;
            }
        }
        found = mix(span.x, span.y, along(high));
        return;
    }
    found = -1.0;
}
