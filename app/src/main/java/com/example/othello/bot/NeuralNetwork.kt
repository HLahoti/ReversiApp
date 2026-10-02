package com.example.othello.bot

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

/** Single-hidden-layer fully-connected net.  Mirrors C++ NeuralNetwork. */
class NeuralNetwork(
    val inputSize: Int,
    val hiddenSize: Int,
    val outputSize: Int,
    val activation: Activation,
    genome: FloatArray,
) {
    private val w1: FloatArray    // hidden * input
    private val b1: FloatArray    // hidden
    private val w2: FloatArray    // output * hidden
    private val b2: FloatArray    // output

    init {
        val expected = hiddenSize * inputSize + hiddenSize +
                outputSize * hiddenSize + outputSize
        require(genome.size == expected) {
            "genome size ${genome.size} != expected $expected"
        }
        var o = 0
        w1 = genome.copyOfRange(o, o + hiddenSize * inputSize); o += w1.size
        b1 = genome.copyOfRange(o, o + hiddenSize); o += b1.size
        w2 = genome.copyOfRange(o, o + outputSize * hiddenSize); o += w2.size
        b2 = genome.copyOfRange(o, o + outputSize)
    }

    private fun act(x: Float): Float = when (activation) {
        Activation.SIGMOID -> (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()
        Activation.RELU -> if (x > 0f) x else 0f
        Activation.TANH -> tanh(x.toDouble()).toFloat()
    }

    /** Returns the first output; the engine uses a scalar evaluator. */
    fun forwardScalar(input: FloatArray): Float {
        require(input.size == inputSize)
        val hidden = FloatArray(hiddenSize)
        for (h in 0 until hiddenSize) {
            var sum = b1[h]
            val rowOffset = h * inputSize
            for (i in 0 until inputSize) sum += w1[rowOffset + i] * input[i]
            hidden[h] = act(sum)
        }
        var sum = b2[0]
        for (h in 0 until hiddenSize) sum += w2[h] * hidden[h]
        return act(sum)
    }
}
