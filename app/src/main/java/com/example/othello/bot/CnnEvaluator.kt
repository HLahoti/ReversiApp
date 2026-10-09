package com.example.othello.bot

import com.example.othello.game.Board
import com.example.othello.game.Player
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * A [PositionEvaluator] backed by the residual [CnnNetwork]: the CNN sibling of
 * [NeuralNetworkEvaluator]. It owns the CNN's JSON schema and pairs the network
 * with [CnnPlanesEncoder]. See [ModelLoader] for how one gets selected.
 */
class CnnEvaluator(
    private val network: CnnNetwork,
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
        /** Discriminator value in the genome JSON's `network.kind`. */
        const val TYPE = "cnn"

        /** The CNN shape as the engine's `CnnNetwork::toJson` writes it. */
        @Serializable
        private data class Spec(
            val planes: Int = 4,
            val stem_filters: Int = 16,
            val residual_blocks: Int = 2,
            val head_hidden: Int = 32,
            val num_features: Int = 13,
            val output_size: Int = 1,
            val conv_activation: String = "relu",
            val output_activation: String = "tanh",
            val genome: FloatArray,
        )

        /** Build a CNN evaluator from the `network` object of a genome file. */
        fun fromJson(json: Json, network: JsonObject): CnnEvaluator {
            val spec = json.decodeFromJsonElement(Spec.serializer(), network)
            val net = CnnNetwork(
                planes = spec.planes,
                filters = spec.stem_filters,
                residualBlocks = spec.residual_blocks,
                headHidden = spec.head_hidden,
                numFeatures = spec.num_features,
                outputSize = spec.output_size,
                convActivation = Activation.fromString(spec.conv_activation),
                outputActivation = Activation.fromString(spec.output_activation),
                genome = spec.genome,
            )
            return CnnEvaluator(net, CnnPlanesEncoder)
        }
    }
}
