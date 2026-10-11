package com.example.reversi.bot

import com.example.reversi.game.Board
import com.example.reversi.game.Player
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * A [PositionEvaluator] backed by a dense feed-forward network: it encodes the
 * board to a flat vector with a [BoardEncoder], then runs a [NeuralNetwork]
 * over it. This class *is* the "MLP" architecture end to end — the JSON schema,
 * the layer-shape derivation, the encoder choice, and the forward call all live
 * here — so a different architecture is a sibling class, never an edit to this
 * one. See [ModelLoader] for how one gets selected.
 */
class NeuralNetworkEvaluator(
    private val network: NeuralNetwork,
    private val encoder: BoardEncoder,
) : PositionEvaluator {

    init {
        require(network.inputSize == encoder.size) {
            "network input ${network.inputSize} != encoder size ${encoder.size}"
        }
    }

    override fun evaluate(board: Board, player: Player): Float =
        network.forwardScalar(encoder.encode(board, player))

    companion object {
        /** Discriminator value in the genome JSON's `network.type`, if present. */
        const val TYPE = "mlp"

        /**
         * The dense-network shape as stored in the genome JSON. The hidden
         * layers are a list, so this is the only place that maps JSON fields to
         * a layer stack: add a `hidden_size_3` here and the generic
         * [NeuralNetwork] and the search keep working unchanged.
         */
        @Serializable
        private data class Spec(
            val input_size: Int = 205,
            val hidden_size: Int = 48,
            val hidden_size_2: Int? = null,
            val output_size: Int = 1,
            val activation: String = "tanh",
            val genome: FloatArray,
        ) {
            /** `[input, hidden…, output]`, dropping any non-positive (disabled) layer. */
            fun layerSizes(): IntArray {
                val hiddens = listOfNotNull(
                    hidden_size.takeIf { it > 0 },
                    hidden_size_2?.takeIf { it > 0 },
                )
                return (listOf(input_size) + hiddens + listOf(output_size)).toIntArray()
            }
        }

        /** Build an MLP evaluator from the `network` object of a genome file. */
        fun fromJson(json: Json, network: JsonObject): NeuralNetworkEvaluator {
            val spec = json.decodeFromJsonElement(Spec.serializer(), network)
            val net = NeuralNetwork(
                layerSizes = spec.layerSizes(),
                activation = Activation.fromString(spec.activation),
                genome = spec.genome,
            )
            return NeuralNetworkEvaluator(net, PlanesAndFeaturesEncoder)
        }
    }
}
