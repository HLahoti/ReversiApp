package com.example.othello.bot

import android.content.Context
import com.example.othello.config.AppConfig
import com.example.othello.game.Board
import com.example.othello.game.Move
import com.example.othello.game.Player
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

@Serializable
private data class GenomeNetwork(
    val input_size: Int = 64,
    val hidden_size: Int = 32,
    val output_size: Int = 1,
    val activation: String = "tanh",
    val genome: FloatArray,
) {
    override fun equals(other: Any?) = other is GenomeNetwork &&
            input_size == other.input_size &&
            hidden_size == other.hidden_size &&
            output_size == other.output_size &&
            activation == other.activation &&
            genome.contentEquals(other.genome)
    override fun hashCode(): Int = genome.contentHashCode()
}

@Serializable
private data class GenomeFile(
    val id: Int = -1,
    val fitness: Double = 0.0,
    val search_depth: Int = 3,
    val network: GenomeNetwork,
)

/** A neural-network evaluator + alpha-beta search picked up from a genome JSON. */
class Bot(
    private val network: NeuralNetwork,
    val searchDepth: Int,
    val id: Int = -1,
) {
    private val search = AlphaBetaSearch(searchDepth)

    fun chooseMove(board: Board, player: Player): Move =
        search.chooseMove(board, player) { b, p ->
            network.forwardScalar(b.encode(p))
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Load the bundled genome from the path in [AppConfig.Bot.GENOME_ASSET_PATH]. */
        @OptIn(ExperimentalSerializationApi::class)
        fun loadDefault(
            ctx: Context,
            assetPath: String = AppConfig.Bot.GENOME_ASSET_PATH,
        ): Bot {
            ctx.assets.open(assetPath).use { stream ->
                val gf = json.decodeFromStream<GenomeFile>(stream)
                val n = gf.network
                val net = NeuralNetwork(
                    inputSize = n.input_size,
                    hiddenSize = n.hidden_size,
                    outputSize = n.output_size,
                    activation = Activation.fromString(n.activation),
                    genome = n.genome,
                )
                val depth = (AppConfig.Bot.SEARCH_DEPTH_OVERRIDE ?: gf.search_depth)
                    .coerceAtLeast(1)
                return Bot(net, depth, gf.id)
            }
        }
    }
}
