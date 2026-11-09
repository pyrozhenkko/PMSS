package com.example.lab1.helpers

import android.content.Context
import android.util.Log
import com.example.lab1.AppConstants
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

private const val TAG = "SafeBox/SafeAccessClassifier"

/** Classes the model distinguishes (the order matches labels.txt). */
enum class AccessClass(val title: String) {
    CLOSED("Safe closed"),
    FLICKER("Accidental flash"),
    OPENED("Safe opening"),
    TAMPERING("Tampering attempt")
}

/** Classification result: the most probable class and the probabilities of all classes. */
data class Prediction(
    val accessClass: AccessClass,
    val confidence: Float,
    val probabilities: List<Float>
)

/**
 * ML wrapper: loads the TensorFlow Lite model from assets into memory and runs inference
 * on the device, without calling the cloud.
 */
class SafeAccessClassifier(context: Context) : Closeable {

    private val interpreter = Interpreter(loadModelFile(context))

    private val labels: List<AccessClass> = context.assets.open(LABELS_FILE).bufferedReader()
        .useLines { lines -> lines.filter(String::isNotBlank).map(AccessClass::valueOf).toList() }

    /** sensorWindow — 20 normalized samples from LightWindowBuffer.toModelInput(). */
    fun classify(sensorWindow: FloatArray): Prediction {
        require(sensorWindow.size == AppConstants.ML_WINDOW) {
            "Expected a window of ${AppConstants.ML_WINDOW} values, got ${sensorWindow.size}"
        }
        val input = arrayOf(sensorWindow)                   // float32 tensor [1, 20]
        val output = arrayOf(FloatArray(labels.size))       // float32 tensor [1, 4]
        interpreter.run(input, output)
        val probabilities = output[0]
        val best = probabilities.indices.maxBy { probabilities[it] }
        return Prediction(labels[best], probabilities[best], probabilities.toList())
    }

    override fun close() = interpreter.close()

    /** Maps the model file into memory (memory-mapped) without copying it to the Java heap. */
    private fun loadModelFile(context: Context): MappedByteBuffer =
        context.assets.openFd(MODEL_FILE).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
            }
        }

    companion object {
        private const val MODEL_FILE = "safe_access_model.tflite"
        private const val LABELS_FILE = "labels.txt"

        /** A model failure must not crash the app: without it the threshold logic of Lab 4 works. */
        fun createOrNull(context: Context): SafeAccessClassifier? = try {
            SafeAccessClassifier(context)
        } catch (e: IOException) {
            Log.e(TAG, "Model file not found: ${e.message}")
            null
        } catch (e: RuntimeException) {
            Log.e(TAG, "Failed to initialize TensorFlow Lite", e)
            null
        }
    }
}
