#version 330

// The part of the picture shown: left, bottom, width and height in texture coordinates, a negative height flipping it
uniform vec4 area;

out vec2 uv;

void main()
{
    vec2 corner = vec2(gl_VertexID & 1, (gl_VertexID >> 1) & 1);
    uv = area.xy + corner * area.zw;
    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
}
