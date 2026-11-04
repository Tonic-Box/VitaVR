#version 330

layout(location = 0) in vec3 position;
uniform mat4 worldToClip;

void main()
{
    gl_Position = worldToClip * vec4(position, 1.0);
}
