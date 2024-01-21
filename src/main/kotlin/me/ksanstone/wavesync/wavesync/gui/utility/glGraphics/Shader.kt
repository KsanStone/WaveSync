package me.ksanstone.wavesync.wavesync.gui.utility.glGraphics

import org.lwjgl.opengl.GL43

class Shader(vertexSource: String, fragmentSource: String) {
    val program: Int

    init {
        println("Vertex shader compile")
        val vertexShader = GL43.glCreateShader(GL43.GL_VERTEX_SHADER)
        GL43.glShaderSource(vertexShader, vertexSource)
        GL43.glCompileShader(vertexShader)

        println("Fragment shader compile")
        val fragmentShader = GL43.glCreateShader(GL43.GL_FRAGMENT_SHADER)
        GL43.glShaderSource(fragmentShader, fragmentSource)
        GL43.glCompileShader(fragmentShader)

        println("Program create")
        program = GL43.glCreateProgram()
        GL43.glAttachShader(program, vertexShader)
        GL43.glAttachShader(program, fragmentShader)
        GL43.glLinkProgram(program)
        GL43.glDeleteShader(vertexShader)
        GL43.glDeleteShader(fragmentShader)
    }
}