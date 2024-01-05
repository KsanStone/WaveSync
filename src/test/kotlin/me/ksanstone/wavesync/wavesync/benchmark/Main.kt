package me.ksanstone.wavesync.wavesync.benchmark

import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.Options
import org.openjdk.jmh.runner.options.OptionsBuilder
import org.openjdk.jmh.runner.options.TimeValue
import java.util.concurrent.TimeUnit

object BenchmarkRunner {
    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        launchBenchmark()
    }

    @Throws(Exception::class)
    @JvmStatic
    fun launchBenchmark() {
        val opt: Options = OptionsBuilder() // Specify which benchmarks to run.
            // You can be more specific if you'd like to run only one benchmark per test.
            //                .include(SerializerBenchmarks.class.getName() + ".*")
            .include(Log2::class.java.getName() + ".*")
            // Set the following options as needed
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .warmupTime(TimeValue.seconds(1))
            .warmupIterations(3)
            .measurementTime(TimeValue.seconds(1))
            .measurementIterations(3)
            .threads(1)
            .forks(1)
            .shouldFailOnError(true)
            .shouldDoGC(true) //				 .jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
            //				 .addProfiler(WinPerfAsmProfiler.class)
            .build()

        Runner(opt).run()
    }
}