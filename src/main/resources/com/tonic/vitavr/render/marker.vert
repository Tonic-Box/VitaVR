#version 330

layout(location = 0) in vec3 position;
layout(location = 1) in vec2 corner;
uniform mat4 worldToClip;
out vec2 at;

void main()
{
    at = corner;
    gl_Position = worldToClip * vec4(position, 1.0);
}
