#version 330

uniform sampler2D atlas;
uniform float fade;
in vec2 uv;
out vec4 fragment;

// The atlas is premultiplied, as Java paints it, so a fade scales the whole colour
void main()
{
    fragment = texture(atlas, uv) * fade;
}
