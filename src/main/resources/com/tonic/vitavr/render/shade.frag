#version 330

in vec2 at;
uniform vec2 centre;
uniform vec2 halfSize;
uniform float vignette;
uniform float black;
out vec4 fragment;

// The edges closed in round where the eye looks straight ahead, and the whole blackened
void main()
{
    float far = length((at - centre) * halfSize);
    float edge = vignette * smoothstep(mix(1.6, 0.55, vignette), mix(2.2, 1.05, vignette), far);
    fragment = vec4(0.0, 0.0, 0.0, max(edge, black));
}
