package me.ksanstone.wavesync.wavesync.gui.utility.glGraphics

import org.lwjgl.opengl.GL43
import java.nio.charset.Charset

class ComputeShader(private val shaderSourcePath: String) {

    var program: Int = 0

    fun use() {
        GL43.glActiveTexture(GL43.GL_TEXTURE0)
        GL43.glBindTexture(GL43.GL_TEXTURE_2D, texId)
    }

    fun createProgram(): Int {
        val code = ClassLoader.getSystemClassLoader().getResource(shaderSourcePath)
            ?.readText(charset = Charset.forName("UTF-8")) ?: return NO_PROGRAM

        println(code)

        val compute = GL43.glCreateShader(GL43.GL_COMPUTE_SHADER)
        GL43.glShaderSource(compute, code)
        GL43.glCompileShader(compute)

        val program = GL43.glCreateProgram()
        GL43.glAttachShader(program, compute)
        GL43.glLinkProgram(program)
//        // Flag shader for deletion once the program closes
//        GL43.glDeleteShader(compute)


        this.program = program

        return program
    }

    var texId = 0
    var fboId = 0
    fun bindBuffers(texWidth: Int, texHeight: Int) {
        if (fboId != 0) GL43.glDeleteFramebuffers(fboId)
        if (texId != 0) GL43.glDeleteTextures(texId)

        println("bind $texWidth $texHeight")

        texId = GL43.glGenTextures()
        use()

        GL43.glTexParameteri(GL43.GL_TEXTURE_2D, GL43.GL_TEXTURE_WRAP_S, GL43.GL_CLAMP_TO_EDGE)
        GL43.glTexParameteri(GL43.GL_TEXTURE_2D, GL43.GL_TEXTURE_WRAP_T, GL43.GL_CLAMP_TO_EDGE)
        GL43.glTexParameteri(GL43.GL_TEXTURE_2D, GL43.GL_TEXTURE_MAG_FILTER, GL43.GL_LINEAR)
        GL43.glTexParameteri(GL43.GL_TEXTURE_2D, GL43.GL_TEXTURE_MIN_FILTER, GL43.GL_LINEAR)
        GL43.glTexImage2D(
            GL43.GL_TEXTURE_2D, 0, GL43.GL_RGBA32F, texWidth, texHeight, 0, GL43.GL_RGBA,
            GL43.GL_FLOAT, 0
        )
        GL43.glBindImageTexture(0, texId, 0, false, 0, GL43.GL_READ_WRITE, GL43.GL_RGBA32F)

        fboId = GL43.glGenFramebuffers()
        GL43.glBindFramebuffer(GL43.GL_READ_FRAMEBUFFER, fboId)
        GL43.glFramebufferTexture2D(GL43.GL_READ_FRAMEBUFFER, GL43.GL_COLOR_ATTACHMENT0, GL43.GL_TEXTURE_2D, texId, 0)
    //        GL43.glBindFramebuffer(GL43.GL_DRAW_FRAMEBUFFER, 0) Already handled by the GlCanvas
    }

    companion object {
        const val NO_PROGRAM = 0
    }

}