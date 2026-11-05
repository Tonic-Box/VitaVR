package com.tonic.vitavr.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Builds VitaVR's shader programs from the .glsl files beside this class.
 */
public final class Shaders
{
    private Shaders()
    {
    }

    /**
     * Compiles and links a program from a vertex and a fragment shader.
     *
     * @param vertex The vertex shader file name.
     * @param fragment The fragment shader file name.
     * @return The program id.
     * @throws IllegalStateException If a shader does not compile or the program does not link.
     */
    public static int program(String vertex, String fragment)
    {
        int program = GL20.glCreateProgram();
        int vertexShader = compile(GL20.GL_VERTEX_SHADER, vertex);
        int fragmentShader = compile(GL20.GL_FRAGMENT_SHADER, fragment);
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
        {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new IllegalStateException("Linking " + vertex + " and " + fragment + " failed: " + log);
        }
        return program;
    }

    private static int compile(int type, String file)
    {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, read(file));
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
        {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Compiling " + file + " failed: " + log);
        }
        return shader;
    }

    private static String read(String file)
    {
        try (InputStream in = Shaders.class.getResourceAsStream(file))
        {
            if (in == null)
            {
                throw new IllegalStateException("Shader " + file + " is missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Reading shader " + file + " failed", e);
        }
    }
}
