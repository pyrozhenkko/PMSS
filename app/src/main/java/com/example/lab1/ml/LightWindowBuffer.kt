package com.example.lab1.ml

import com.example.lab1.core.AppConstants
import kotlin.math.ln

/**
 * Ring buffer of the last N light samples. A new sample overwrites the oldest one,
 * so no memory is reallocated and the window "slides" in time.
 */
class LightWindowBuffer(private val size: Int = AppConstants.ML_WINDOW) {

    private val values = FloatArray(size)
    private var next = 0        // index where the next sample will be written
    private var count = 0

    /** The model may be called only when the window is completely filled. */
    val isFull: Boolean get() = count == size

    fun add(lux: Float) {
        values[next] = lux
        next = (next + 1) % size
        if (count < size) count++
    }

    /**
     * Converts raw sensor data into the model input format: samples in chronological
     * order (oldest first), normalized to [0, 1].
     */
    fun toModelInput(): FloatArray = FloatArray(size) { i -> normalize(values[(next + i) % size]) }

    companion object {
        private val LOG_MAX = ln(1f + AppConstants.ML_LUX_MAX)

        /**
         * The same formula as in train_model.py: ln(1 + lux) / ln(1 + 1000).
         * A logarithm, because illuminance varies by orders of magnitude (3 lux in the safe, hundreds in a room).
         */
        fun normalize(lux: Float): Float = (ln(1f + lux.coerceAtLeast(0f)) / LOG_MAX).coerceIn(0f, 1f)
    }
}
