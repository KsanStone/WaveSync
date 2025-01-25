package me.ksanstone.wavesync.wavesync.gui.utility

import org.lwjgl.opengl.ARBFramebufferObject.GL_INVALID_FRAMEBUFFER_OPERATION
import org.lwjgl.opengl.GL20.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object GlUtil {

    val logger: Logger = LoggerFactory.getLogger(javaClass)

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

    fun checkGLError(tag: String = "OpenGL") {
        var errorCode: Int
        while (glGetError().also { errorCode = it } != GL_NO_ERROR) {
            val errorString = when (errorCode) {
                GL_INVALID_ENUM -> "GL_INVALID_ENUM"
                GL_INVALID_VALUE -> "GL_INVALID_VALUE"
                GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
                GL_STACK_OVERFLOW -> "GL_STACK_OVERFLOW"
                GL_STACK_UNDERFLOW -> "GL_STACK_UNDERFLOW"
                GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
                GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"
                else -> "UNKNOWN_ERROR"
            }
            logger.warn("$tag: OpenGL Error: $errorString (Code: $errorCode)")
        }
    }


}