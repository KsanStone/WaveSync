package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.bytedeco.fftw.global.fftw3
import org.bytedeco.fftw.global.fftw3.*
import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.javacpp.Loader
import org.springframework.stereotype.Service
import kotlin.math.sqrt

@Service
class FFTTransformerService {

    var fftPlan: fftwf_plan? = null

    private lateinit var signal: FloatPointer
    private lateinit var result: FloatPointer
    private var numPoints: Int = 0
    private lateinit var resulAuxArray: FloatArray

    fun initializePlan(signal1: FloatPointer, resultReal: FloatPointer, numPoints1: Int) {
        if (fftPlan != null)
            fftwf_destroy_plan(fftPlan)
        fftPlan = fftwf_plan_dft_1d(numPoints1, signal1, resultReal, FFTW_FORWARD, FFTW_ESTIMATE)
        signal = signal1
        result = resultReal
        numPoints = numPoints1
        resulAuxArray = FloatArray(result.capacity().toInt())
    }

    fun scaleAndPutSamples(samples: FloatArray, scalar: Float) {
        val s = 2.0f / scalar
        for (i in samples.indices step 2) {
            samples[i] *= s
        }
        signal.put(samples, 0, samples.size)
    }

    fun computeMagnitudes(output: FloatArray) {
        result.get(resulAuxArray)
        for (i in 0 until numPoints / 2) {
            output[i] = sqrt(resulAuxArray[2 * i + REAL] * resulAuxArray[2 * i + REAL] + resulAuxArray[2 * i + IMAG] * resulAuxArray[2 * i + IMAG])
        }
    }

    fun transform() {
        fftwf_execute(fftPlan)
    }

    @PostConstruct
    fun initialize() {
        Loader.load(fftw3::class.java)
    }

    @PreDestroy
    fun cleanup() {
        if (fftPlan != null)
            fftwf_destroy_plan(fftPlan)
    }


    companion object {
        const val REAL: Int = 0
        const val IMAG: Int = 1
    }
}