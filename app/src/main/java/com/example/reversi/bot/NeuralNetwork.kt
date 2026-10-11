package com.example.reversi.bot

import kotlin.math.exp
import kotlin.math.tanh

enum class Activation {
    TANH, SIGMOID, RELU;

    companion object {
        fun fromString(s: String): Activation = when (s.lowercase()) {
            "sigmoid" -> SIGMOID
            "relu" -> RELU
            else -> TANH
        }
    }
}

/**
 * A plain feed-forward (fully-connected) network of arbitrary depth. The shape
 * is described entirely by [layerSizes] — `[inputSize, hidden1, hidden2, …,
 * outputSize]` — so one, two, or N hidden layers are all the same code path.
 * The architecture lives in the data, not here: changing widths or adding a
 * hidden layer only changes the numbers in [layerSizes] (and the genome), never
 * this class.
 *
 * The flat [genome] is sliced layer by layer in the order `weights, biases`,
 * weights row-major with one row per output neuron. This mirrors the C++
 * `NeuralNetwork` in OthelloEngine exactly (w1,b1,w2,b2,…,wN,bN), so a genome
 * exported there loads here unchanged. The [activation] is applied after every
 * layer, including the output.
 */
class NeuralNetwork(
    private val layerSizes: IntArray,
    private val activation: Activation,
    genome: FloatArray,
) {
    init {
        require(layerSizes.size >= 2) {
            "layerSizes must have at least an input and an output: ${layerSizes.toList()}"
        }
    }

    val inputSize: Int get() = layerSizes.first()
    val outputSize: Int get() = layerSizes.last()

    // One weight matrix (out*in, row-major) and bias vector (out) per layer.
    private val weights: Array<FloatArray>
    private val biases: Array<FloatArray>

    init {
        require(genome.size == genomeSize(layerSizes)) {
            "genome size ${genome.size} != expected ${genomeSize(layerSizes)} " +
                "for shape ${layerSizes.toList()}"
        }
        val layers = layerSizes.size - 1
        weights = Array(layers) { FloatArray(0) }
        biases = Array(layers) { FloatArray(0) }
        var o = 0
        for (l in 0 until layers) {
            val inN = layerSizes[l]
            val outN = layerSizes[l + 1]
            weights[l] = genome.copyOfRange(o, o + outN * inN); o += outN * inN
            biases[l] = genome.copyOfRange(o, o + outN); o += outN
        }
    }

    private fun act(x: Float): Float = when (activation) {
        Activation.SIGMOID -> (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()
        Activation.RELU -> if (x > 0f) x else 0f
        Activation.TANH -> tanh(x.toDouble()).toFloat()
    }

    fun forward(input: FloatArray): FloatArray {
        require(input.size == inputSize) {
            "input size ${input.size} != $inputSize"
        }
        var current = input
        for (l in weights.indices) {
            val inN = layerSizes[l]
            val outN = layerSizes[l + 1]
            val w = weights[l]
            val b = biases[l]
            val next = FloatArray(outN)
            for (o in 0 until outN) {
                var sum = b[o]
                val rowOffset = o * inN
                for (i in 0 until inN) sum += w[rowOffset + i] * current[i]
                next[o] = act(sum)
            }
            current = next
        }
        return current
    }

    /** Convenience for the common scalar-evaluator case (outputSize == 1). */
    fun forwardScalar(input: FloatArray): Float = forward(input)[0]

    companion object {
        /** Number of floats a genome needs for the given [layerSizes]. */
        fun genomeSize(layerSizes: IntArray): Int {
            var n = 0
            for (l in 0 until layerSizes.size - 1) {
                val inN = layerSizes[l]
                val outN = layerSizes[l + 1]
                n += outN * inN + outN
            }
            return n
        }
    }
}
