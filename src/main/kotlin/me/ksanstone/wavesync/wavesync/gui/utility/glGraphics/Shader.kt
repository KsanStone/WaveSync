package me.ksanstone.wavesync.wavesync.gui.utility.glGraphics

import org.lwjgl.opengl.GL33

class Shader(vertexSource: String, fragmentSource: String) {
    val program: Int

    init {
        println("Vertex shader compile")
        val vertexShader = GL33.glCreateShader(GL33.GL_VERTEX_SHADER)
        GL33.glShaderSource(vertexShader, vertexSource)
        GL33.glCompileShader(vertexShader)

        println("Fragment shader compile")
        val fragmentShader = GL33.glCreateShader(GL33.GL_FRAGMENT_SHADER)
        GL33.glShaderSource(fragmentShader, fragmentSource)
        GL33.glCompileShader(fragmentShader)

        println("Program create")
        program = GL33.glCreateProgram()
        GL33.glAttachShader(program, vertexShader)
        GL33.glAttachShader(program, fragmentShader)
        GL33.glLinkProgram(program)
        GL33.glDeleteShader(vertexShader)
        GL33.glDeleteShader(fragmentShader)
    }
}