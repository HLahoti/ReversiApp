package com.example.othello.bot

/**
 * Bot strength tiers. Each tier is backed by an elite engine snapshot
 * exported from OthelloEngine at a different generation count — later
 * generations have co-evolved against stronger opponents and play better.
 */
enum class Difficulty(val assetPath: String, val label: String) {
    EASY("bot_genome_easy.json", "Easy"),
    MEDIUM("bot_genome_medium.json", "Medium"),
    HARD("bot_genome_hard.json", "Hard"),
}
