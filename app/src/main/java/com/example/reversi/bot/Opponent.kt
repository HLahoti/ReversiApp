package com.example.reversi.bot

import com.example.reversi.bot.learning.Learning

/**
 * The bundled bot opponents, declared in order of increasing strength — the
 * home screen lists them in exactly this order. Each one is a single elite
 * engine exported from OthelloEngine (a hall-of-fame champion), named after an
 * Indian chess player, lightly morphed.
 *
 * Adding an opponent is one line here plus its asset; the architecture inside
 * the asset (MLP, CNN, …) is [ModelLoader]'s concern, not this list's. The
 * enum `name` is persisted in saved games, so rename entries with care (see
 * [fromSaved]).
 *
 * An opponent with [learning] improves as it plays: it starts at its bundled
 * model's strength and builds on that from every finished game (see [Bots]).
 * It is declared by its starting strength, but the home screen gives
 * learning opponents their own row.
 */
enum class Opponent(
    val assetPath: String,
    val displayName: String,
    val learning: Learning? = null,
) {
    VISHY("bot_vishy.json", "Vishy"),   // MLP, gen 150 champion
    GUK("bot_guk.json", "Guk"),         // MLP, gen 250 champion
    ARJUN("bot_arjun.json", "Arjun"),   // MLP, gen 399 champion
    SHADOW("bot_shadow.json", "Shadow", Learning.OPENING_BOOK), // CNN, gen 73 champion
    PRAGG("bot_pragg.json", "Pragg"),   // CNN, gen 88 champion
    KONERU("bot_koneru.json", "Koneru"), // CNN, gen 150 champion
    ;

    companion object {
        val DEFAULT = GUK

        /**
         * Resolve a persisted name, including the pre-roster EASY/MEDIUM/HARD
         * tiers (which were the same models as Vishy/Guk/Arjun), so older
         * saved games resume against the bot they were started with.
         */
        fun fromSaved(name: String?): Opponent? = when (name) {
            null -> null
            "EASY" -> VISHY
            "MEDIUM" -> GUK
            "HARD" -> ARJUN
            else -> entries.firstOrNull { it.name == name }
        }
    }
}
