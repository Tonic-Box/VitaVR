#version 330

// One triangle over the whole viewport, made from the vertex number alone
out vec2 at;

void main()
{
    vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    at = corner * 2.0 - 1.0;
    gl_Position = vec4(at, 0.0, 1.0);
}
