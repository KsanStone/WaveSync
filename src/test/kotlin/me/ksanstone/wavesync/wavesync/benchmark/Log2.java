package me.ksanstone.wavesync.wavesync.benchmark;
import me.ksanstone.wavesync.wavesync.service.FourierMath;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;

public class Log2 {

    @org.openjdk.jmh.annotations.State(Scope.Thread)
    public static class State {

        Random random = new Random();
        int value = 0;

        @Setup(Level.Invocation)
        public void initialize() {
            value = 1 << random.nextInt(0, 31);
        }
    }

    @Benchmark
    public void consume0(State state, Blackhole blackhole) {
        blackhole.consume(state.value);
    }

    @Benchmark
    public void stackOverflow(State state, Blackhole blackhole) {
        blackhole.consume(FourierMath.INSTANCE.binlog(state.value));
    }

    @Benchmark
    public void whileImpl(State state, Blackhole blackhole) {
        blackhole.consume(FourierMath.INSTANCE.numBitsWhile(state.value));
    }

    @Benchmark
    public void javaImpl(State state, Blackhole blackhole) {
        blackhole.consume(FourierMath.INSTANCE.log2nlz(state.value));
    }

}