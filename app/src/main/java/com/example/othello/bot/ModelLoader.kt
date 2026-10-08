package com.example.othello.bot

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream

/**
 * Reads a genome JSON file and builds the right [PositionEvaluator] for it,
 * choosing the architecture from the model's `network.type` (defaulting to the
 * dense MLP that every current export uses, which carries no such field).
 *
 * This `when` is the *one* place to teach the app a new architecture: implement
 * a [PositionEvaluator] (with its own `fromJson` and board encoding), then add a
 * single branch below. Nothing else — not [Bot], not the search, not the app —
 * changes. The envelope around the architecture-specific `network` object
 * (`id`, `fitness`, `search_depth`) is shared across all architectures.
 */
object ModelLoader {
    private val json = Json { ignoreUnknownKeys = true }

    data class LoadedModel(
        val evaluator: PositionEvaluator,
        val searchDepth: Int,
        val id: Int,
    )

    @Serializable
    private data class Envelope(
        val id: Int = -1,
        val fitness: Double = 0.0,
        val search_depth: Int = 4,
        val network: JsonObject,
    )

    @OptIn(ExperimentalSerializationApi::class)
    fun load(stream: InputStream): LoadedModel {
        val env = json.decodeFromStream<Envelope>(stream)
        val type = env.network["type"]?.jsonPrimitive?.content?.lowercase()
            ?: NeuralNetworkEvaluator.TYPE
        val evaluator = when (type) {
            NeuralNetworkEvaluator.TYPE, "dense", "ffn" ->
                NeuralNetworkEvaluator.fromJson(json, env.network)
            // "cnn" -> CnnEvaluator.fromJson(json, env.network)  // ← future arch: one line
            else -> error("unknown model architecture '$type'")
        }
        return LoadedModel(evaluator, env.search_depth, env.id)
    }
}
