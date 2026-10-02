package com.example.othello.config

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * All tunable parameters for the Othello app live here. Change a value in
 * this file (and rebuild) to tweak the behaviour or look of the app without
 * having to hunt through the rest of the codebase.
 *
 * The sections are self-contained — a change to, say, Bot.SEARCH_DEPTH_OVERRIDE
 * only touches [com.example.othello.bot.Bot] and the view model that uses it.
 */
object AppConfig {

    // ─────────────────────────────────────────────────────────────────────
    // Bot
    // ─────────────────────────────────────────────────────────────────────
    object Bot {
        /** Asset path (relative to `assets/`) of the genome JSON to load. */
        const val GENOME_ASSET_PATH: String = "bot_genome.json"

        /**
         * If non-null, overrides the search depth stored in the genome JSON.
         * The default (null) uses whatever depth the genome declares.
         * Higher = stronger + slower.
         */
        val SEARCH_DEPTH_OVERRIDE: Int? = null

        /** Floor on how long the "Thinking…" state stays visible, in ms. */
        const val THINKING_MIN_MS: Long = 150L

        /** Delay before auto-passing a side with no legal moves, in ms. */
        const val AUTO_PASS_DELAY_MS: Long = 500L

        /**
         * Delay after an opponent's move before the bot starts to think, in ms.
         * Gives the player a moment to see their move and watch the flip
         * animation finish (one flip is ~850 ms). Only applies when there is a
         * previous move — the bot never pauses before the game's opening ply.
         * Set to 0 for an instant reply.
         */
        const val RESPONSE_DELAY_MS: Long = 800L
    }

    // ─────────────────────────────────────────────────────────────────────
    // Animation
    // ─────────────────────────────────────────────────────────────────────
    object Animation {
        /** Number of pre-rendered flip frames (must match the drawable set). */
        const val FLIP_FRAME_COUNT: Int = 20

        /** How long each flip frame is shown, in ms. */
        const val FLIP_FRAME_MS: Long = 40L

        /** Extra ms held after the last flip frame before cleanup. */
        const val FLIP_CLEANUP_PAD_MS: Long = 50L
    }

    // ─────────────────────────────────────────────────────────────────────
    // Board layout + overlay look
    // ─────────────────────────────────────────────────────────────────────
    object Board {
        /** Piece sprite width ÷ cell width, derived from the Blender renders. */
        const val PIECE_TO_CELL_RATIO: Float = 101f / (1058f / 8f)

        /** Legal-move hint-dot diameter as a fraction of cell width. */
        const val LEGAL_HINT_DIAMETER_RATIO: Float = 0.22f

        /** Hint-dot colour (semi-transparent). */
        val LEGAL_HINT_COLOR: Color = Color(0x66FFFFFF)

        /** Last-move ring diameter as a fraction of cell width. */
        const val LAST_MOVE_RING_DIAMETER_RATIO: Float = 0.82f

        /** Last-move ring stroke width (px, not dp). */
        const val LAST_MOVE_RING_STROKE_PX: Float = 5f

        /** Last-move ring opacity (0–1). */
        const val LAST_MOVE_RING_ALPHA: Float = 0.85f

        /** Toggle hint dots globally. */
        const val SHOW_LEGAL_HINTS: Boolean = true

        /** Toggle the last-move ring globally. */
        const val SHOW_LAST_MOVE_RING: Boolean = true
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pause overlay
    // ─────────────────────────────────────────────────────────────────────
    object Pause {
        /** Blur radius applied to the game content behind the overlay. */
        val BLUR_RADIUS: Dp = 24.dp

        /** Dim overlay alpha (0–1) painted on top of the blur. */
        const val OVERLAY_ALPHA: Float = 0.45f
    }

    // ─────────────────────────────────────────────────────────────────────
    // Haptics
    // ─────────────────────────────────────────────────────────────────────
    object Haptics {
        /** Master switch for all haptic feedback. */
        const val ENABLED: Boolean = true
    }

    // ─────────────────────────────────────────────────────────────────────
    // Palette (every colour used by the UI)
    // ─────────────────────────────────────────────────────────────────────
    object Palette {
        // Felt palette — all surfaces and typography pick from these.
        val Deep        = Color(0xFF0B1F1A)
        val Mid         = Color(0xFF143328)
        val Soft        = Color(0xFF1F4C3C)
        val Highlight   = Color(0xFF6EE7B7)
        val Glass       = Color(0x33FFFFFF)
        val GlassStrong = Color(0x55FFFFFF)
        val OnDark      = Color(0xFFEFF7F2)
        val OnDarkDim   = Color(0xBFEFF7F2)

        // Radial backdrop gradient (every screen).
        val BackdropInner  = Color(0xFF1C4E3B)
        val BackdropMid    = Color(0xFF0D241C)
        val BackdropOuter  = Color(0xFF060E0B)
        val BackdropCenter: Offset = Offset(540f, 300f)
        const val BACKDROP_RADIUS: Float = 1600f

        // Glass-panel gradient (translucent cards).
        val GlassTop    = Color(0x33FFFFFF)
        val GlassBottom = Color(0x14FFFFFF)
        val GlassBorder = Color(0x33FFFFFF)

        // Score-badge disc colours.
        val BlackBadge  = Color(0xFF101010)
        val WhiteBadge  = Color(0xFFE8E8E8)
    }
}
