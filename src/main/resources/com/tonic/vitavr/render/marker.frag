#version 330

in vec2 at;
uniform vec4 colour;
uniform int shape;
out vec4 fragment;

// Shape 0 is a round dot, 1 a cross of two bars
void main()
{
    if (shape == 0)
    {
        if (dot(at, at) > 1.0)
        {
            discard;
        }
    }
    else if (abs(at.x - at.y) > 0.28 && abs(at.x + at.y) > 0.28)
    {
        discard;
    }
    fragment = colour;
}
