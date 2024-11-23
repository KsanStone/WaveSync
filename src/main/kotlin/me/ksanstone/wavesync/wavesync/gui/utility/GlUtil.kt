package me.ksanstone.wavesync.wavesync.gui.utility

import org.lwjgl.opengl.GL20.*

object GlUtil {

    fun compileShader(resourcePath: String, shaderType: Int): Int {
        val shaderSource = this::class.java.getResource(resourcePath)?.readText()
            ?: throw IllegalArgumentException("Shader file not found at $resourcePath")

        val shaderId = glCreateShader(shaderType)
        glShaderSource(shaderId, shaderSource)
        glCompileShader(shaderId)

        val compileStatus = glGetShaderi(shaderId, GL_COMPILE_STATUS)
        if (compileStatus == GL_FALSE) {
            val errorLog = glGetShaderInfoLog(shaderId)
            glDeleteShader(shaderId)
            throw RuntimeException("Shader compilation failed: $errorLog")
        }

        return shaderId
    }

    fun linkProgram(shaderIds: List<Int>): Int {
        val programId = glCreateProgram()

        // Attach shaders to the program
        shaderIds.forEach { shaderId ->
            glAttachShader(programId, shaderId)
        }

        // Link the program
        glLinkProgram(programId)

        // Check for linking errors
        val linkStatus = glGetProgrami(programId, GL_LINK_STATUS)
        if (linkStatus == GL_FALSE) {
            val errorLog = glGetProgramInfoLog(programId)
            glDeleteProgram(programId)
            throw RuntimeException("Program linking failed: $errorLog")
        }

        // Detach shaders after linking
        shaderIds.forEach { shaderId ->
            glDetachShader(programId, shaderId)
        }

        return programId
    }

}