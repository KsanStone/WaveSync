package me.ksanstone.wavesync.wavesync.utility

import jakarta.annotation.PostConstruct
import java.util.concurrent.CountDownLatch

abstract class AsyncInit {

    val initLatch = CountDownLatch(1)

    @PostConstruct
    open fun init() {
        Thread(this::doAsyncInit).start()
    }

    protected fun doAsyncInit() {
        asyncInit()
        initLatch.countDown()
    }

    abstract fun asyncInit()

}