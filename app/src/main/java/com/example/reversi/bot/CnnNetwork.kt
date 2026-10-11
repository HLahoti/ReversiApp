package com.example.reversi.bot

import kotlin.math.exp
import kotlin.math.tanh

/**
 * Small residual CNN over the 8×8 board, a direct port of OthelloEngine's
 * `CnnNetwork`:
 *
 *   stem:  3×3 conv, planes → filters, conv activation (zero "same" padding)
 *   body:  [residualBlocks] × { y = conv2(act(conv1(x))); x = act(y + x) }
 *   head:  global average pool → `filters` scalars, concatenated with the
 *          hand-crafted features; FC(headHidden, conv activation), then
 *          FC(outputSize, output activation).
 *
 * The flat [genome] layout matches the engine exactly: stem (w, b), each block
 * (c1 w, c1 b, c2 w, c2 b), then fc1 (w, b), fc2 (w, b). Conv weights are
 * `[out, in, 3, 3]` row-major; FC weights are one row per output neuron.
 */
class CnnNetwork(
    private val planes: Int,
    private val filters: Int,
    residualBlocks: Int,
    private val headHidden: Int,
    private val numFeatures: Int,
    private val outputSize: Int,
    private val convActivation: Activation,
    private val outputActivation: Activation,
    genome: FloatArray,
) {
    private class Conv(val inC: Int, val outC: Int, val w: FloatArray, val b: FloatArray)

    val inputSize: Int = planes * AREA + numFeatures

    private val stem: Conv
    private val blocks: List<Pair<Conv, Conv>>
    private val fc1W: FloatArray
    private val fc1B: FloatArray
    private val fc2W: FloatArray
    private val fc2B: FloatArray

    init {
        val expected = genomeSize(planes, filters, residualBlocks, headHidden, numFeatures, outputSize)
        require(genome.size == expected) {
            "CNN genome size ${genome.size} != expected $expected"
        }
        var o = 0
        fun take(n: Int) = genome.copyOfRange(o, o + n).also { o += n }
        fun conv(inC: Int, outC: Int) = Conv(inC, outC, take(outC * inC * K * K), take(outC))

        stem = conv(planes, filters)
        blocks = List(residualBlocks) { conv(filters, filters) to conv(filters, filters) }
        val headIn = filters + numFeatures
        fc1W = take(headHidden * headIn)
        fc1B = take(headHidden)
        fc2W = take(outputSize * headHidden)
        fc2B = take(outputSize)
    }

    fun forward(input: FloatArray): FloatArray {
        require(input.size == inputSize) { "input size ${input.size} != $inputSize" }

        // Stem.
        var cur = conv(input, stem)
        for (i in cur.indices) cur[i] = act(convActivation, cur[i])

        // Residual body.
        for ((c1, c2) in blocks) {
            val y = conv(cur, c1)
            for (i in y.indices) y[i] = act(convActivation, y[i])
            val z = conv(y, c2)
            for (i in z.indices) z[i] = act(convActivation, z[i] + cur[i])
            cur = z
        }

        // Global average pool, then append the hand-crafted features.
        val headIn = filters + numFeatures
        val head = FloatArray(headIn)
        for (c in 0 until filters) {
            var sum = 0f
            val base = c * AREA
            for (i in 0 until AREA) sum += cur[base + i]
            head[c] = sum / AREA
        }
        System.arraycopy(input, planes * AREA, head, filters, numFeatures)

        val h = FloatArray(headHidden)
        for (o in 0 until headHidden) {
            var sum = fc1B[o]
            val row = o * headIn
            for (i in 0 until headIn) sum += fc1W[row + i] * head[i]
            h[o] = act(convActivation, sum)
        }
        val out = FloatArray(outputSize)
        for (o in 0 until outputSize) {
            var sum = fc2B[o]
            val row = o * headHidden
            for (i in 0 until headHidden) sum += fc2W[row + i] * h[i]
            out[o] = act(outputActivation, sum)
        }
        return out
    }

    fun forwardScalar(input: FloatArray): Float = forward(input)[0]

    /**
     * Pre-activation 3×3 "same" convolution over `[inC, 8, 8]` → `[outC, 8, 8]`.
     * Reads only the first `inC * 64` values of [input], so the stem can be fed
     * the full encoder vector (planes followed by features) directly. Zero
     * padding is implicit: each kernel tap only touches the in-bounds window.
     */
    private fun conv(input: FloatArray, layer: Conv): FloatArray {
        val out = FloatArray(layer.outC * AREA)
        for (oc in 0 until layer.outC) {
            val outBase = oc * AREA
            val bias = layer.b[oc]
            for (i in 0 until AREA) out[outBase + i] = bias
            for (ic in 0 until layer.inC) {
                val inBase = ic * AREA
                val wBase = (oc * layer.inC + ic) * K * K
                for (ky in 0 until K) {
                    val dy = ky - P
                    val y0 = maxOf(0, -dy)
                    val y1 = minOf(N, N - dy)
                    for (kx in 0 until K) {
                        val w = layer.w[wBase + ky * K + kx]
                        if (w == 0f) continue
                        val dx = kx - P
                        val x0 = maxOf(0, -dx)
                        val x1 = minOf(N, N - dx)
                        for (y in y0 until y1) {
                            val outRow = outBase + y * N
                            val inRow = inBase + (y + dy) * N + dx
                            for (x in x0 until x1) out[outRow + x] += w * input[inRow + x]
                        }
                    }
                }
            }
        }
        return out
    }

    companion object {
        private const val N = 8
        private const val AREA = N * N
        private const val K = 3
        private const val P = K / 2

        private fun act(a: Activation, x: Float): Float = when (a) {
            Activation.RELU -> if (x > 0f) x else 0f
            Activation.SIGMOID -> (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()
            Activation.TANH -> tanh(x.toDouble()).toFloat()
        }

        fun genomeSize(
            planes: Int, filters: Int, residualBlocks: Int,
            headHidden: Int, numFeatures: Int, outputSize: Int,
        ): Int {
            fun conv(inC: Int, outC: Int) = outC * inC * K * K + outC
            return conv(planes, filters) +
                residualBlocks * 2 * conv(filters, filters) +
                headHidden * (filters + numFeatures) + headHidden +
                outputSize * headHidden + outputSize
        }
    }
}
