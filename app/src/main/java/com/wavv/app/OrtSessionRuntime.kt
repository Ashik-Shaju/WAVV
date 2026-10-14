package com.wavv.app

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File

internal class OrtSessionLease(
    val session: OrtSession,
    private val options: OrtSession.SessionOptions,
) : Closeable {
    override fun close() {
        try {
            session.close()
        } finally {
            options.close()
        }
    }
}

internal object OrtSessionRuntime {
    fun create(model: File, preferXnnpack: Boolean): OrtSessionLease {
        val environment = OrtEnvironment.getEnvironment()
        val processorCount = Runtime.getRuntime().availableProcessors()
        return withXnnpackCpuFallback(
            preferXnnpack = preferXnnpack,
            xnnpack = { create(model, environment, processorCount, useXnnpack = true) },
            cpu = { create(model, environment, processorCount, useXnnpack = false) },
        )
    }

    private fun create(
        model: File,
        environment: OrtEnvironment,
        processorCount: Int,
        useXnnpack: Boolean,
    ): OrtSessionLease {
        val options = OrtSession.SessionOptions()
        try {
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            options.setInterOpNumThreads(1)
            options.setMemoryPatternOptimization(true)
            options.setCPUArenaAllocator(true)
            if (useXnnpack) {
                options.setIntraOpNumThreads(1)
                options.addConfigEntry("session.intra_op.allow_spinning", "0")
                options.addXnnpack(
                    mapOf("intra_op_num_threads" to DclapThreadPolicy.xnnpackThreadCount(processorCount).toString()),
                )
            } else {
                options.setIntraOpNumThreads(DclapThreadPolicy.intraOpThreadCount(processorCount))
                options.addCPU(true)
            }
            return OrtSessionLease(environment.createSession(model.absolutePath, options), options)
        } catch (failure: Exception) {
            runCatching { options.close() }
            throw failure
        }
    }
}

internal inline fun <T> withXnnpackCpuFallback(
    preferXnnpack: Boolean,
    xnnpack: () -> T,
    cpu: () -> T,
): T {
    if (!preferXnnpack) return cpu()
    return try {
        xnnpack()
    } catch (xnnpackFailure: Exception) {
        try {
            cpu()
        } catch (cpuFailure: Exception) {
            cpuFailure.addSuppressed(xnnpackFailure)
            throw cpuFailure
        }
    }
}
