#version 330

// Walks the pointer ray across the eye's depth a pixel at a time and gives the distance along it to the first surface, or -1
uniform sampler2D depths;
uniform vec4 start;
uniform vec4 end;
uniform vec2 span;
uniform vec2 size;
uniform float depthScale;
// Turns stored depth into projected depth: 1, 0 when depth is stored zero to one, else 2, -1
uniform vec2 storedToProjected;
out float found;

const int MOST_STEPS = 4096;
const int REFINE_STEPS = 6;
const float THICKNESS = 64.0;

float along(float u)
{
    return u * start.w / ((1.0 - u) * end.w + u * start.w);
}

vec4 at(float u)
{
    return mix(start, end, along(u));
}

float storedAt(vec4 clip)
{
    return texelFetch(depths, ivec2(clamp((clip.xy / clip.w * 0.5 + 0.5) * size, vec2(0.0), size - 1.0)), 0).r;
}

bool inFront(float u)
{
    vec4 clip = at(u);
    float stored = storedAt(clip);
    return stored <= 0.0 || clip.z / clip.w > stored * storedToProjected.x + storedToProjected.y;
}

bool behind(float u)
{
    vec4 clip = at(u);
    float stored = storedAt(clip);
    float surface = stored * storedToProjected.x + storedToProjected.y;
    float ray = clip.z / clip.w;
    if (stored <= 0.0 || surface <= 0.0 || ray > surface)
    {
        return false;
    }
    float surfaceAway = depthScale / surface;
    return depthScale / ray - surfaceAway <= max(THICKNESS, surfaceAway * 0.05);
}

void main()
{
    vec2 from = (start.xy / start.w * 0.5 + 0.5) * size;
    vec2 to = (end.xy / end.w * 0.5 + 0.5) * size;
    int steps = int(clamp(ceil(length(to - from)), 1.0, float(MOST_STEPS)));
    // Only a ray seen in front of the depth first can hit, so one starting inside the arm passes through it
    bool seen = inFront(0.0);
    for (int step = 1; step <= steps; step++)
    {
        float u = float(step) / float(steps);
        if (inFront(u))
        {
            seen = true;
            continue;
        }
        if (!seen)
        {
            continue;
        }

        // Find where the ray went behind the depth within the last pixel
        float low = float(step - 1) / float(steps);
        float high = u;
        for (int refine = 0; refine < REFINE_STEPS; refine++)
        {
            float middle = (low + high) * 0.5;
            if (inFront(middle))
            {
                low = middle;
            }
            else
            {
                high = middle;
            }
        }
        if (behind(high))
        {
            found = mix(span.x, span.y, along(high));
            return;
        }

        // It slipped behind a nearer edge, so it must come out in front again to hit anything
        seen = false;
    }
    found = -1.0;
}
