package com.example.reversi.config

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * All tunable parameters for the Reversi app live here. Change a value in
 * this file (and rebuild) to tweak the behaviour or look of the app without
 * having to hunt through the rest of the codebase.
 *
 * The sections are self-contained — a change to, say, Bot.SEARCH_DEPTH_OVERRIDE
 * only touches [com.example.reversi.bot.Bot] and the view model that uses it.
 */
object AppConfig {

    // ─────────────────────────────────────────────────────────────────────
    // Bot
    // ─────────────────────────────────────────────────────────────────────
    object Bot {
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
    }

    // ─────────────────────────────────────────────────────────────────────
    // Animation
    // ─────────────────────────────────────────────────────────────────────
    object Animation {
        /**
         * Number of pre-rendered flip frames bundled as drawables (fixed —
         * matches the `flip_{b2w,w2b}_001..NNN.png` set).
         */
        const val FLIP_SOURCE_FRAME_COUNT: Int = 100

        /**
         * How many of the bundled frames to actually play per flip. Must be
         * between 1 and [FLIP_SOURCE_FRAME_COUNT]; frames are sampled evenly
         * from the source set. Lower = cheaper to render, choppier look.
         * (Hook this up to a settings toggle later for a performance mode.)
         */
        const val FLIP_FRAME_COUNT: Int = 100

        /** Target total duration of one flip, in ms (ripple delay excluded). */
        const val FLIP_TOTAL_MS: Long = 800L

        /** Per-frame display time, derived to keep total duration constant. */
        val FLIP_FRAME_MS: Long = FLIP_TOTAL_MS / FLIP_FRAME_COUNT

        /** Extra ms held after the last flip frame before cleanup. */
        const val FLIP_CLEANUP_PAD_MS: Long = 50L

        /**
         * Per-cell stagger applied to flips based on Chebyshev distance from
         * the just-played square. Cells adjacent to the move start immediately;
         * each cell further out waits this many ms longer, producing a ripple
         * that fans out along the flipped rays.
         */
        const val FLIP_RIPPLE_PER_CELL_MS: Long = 70L
    }

    // ─────────────────────────────────────────────────────────────────────
    // Game-start intro animation
    // ─────────────────────────────────────────────────────────────────────
    object OpeningAnimation {
        /** Bundled frames of the game-start intro, named `game_start_001..NNN`. */
        const val FRAME_COUNT: Int = 200

        /** Per-frame display time, in ms. 20 ms × 200 = 4.0 s total. */
        const val FRAME_MS: Long = 20L
    }

    // ─────────────────────────────────────────────────────────────────────
    // Board layout + overlay look
    // ─────────────────────────────────────────────────────────────────────
    object Board {
        /**
         * Piece sprite width ÷ cell width, derived from the Blender renders.
         * The 115 is the per-sprite crop region (px) that holds the piece at
         * its widest flip angle plus 1 px padding; the natural resting-piece
         * body inside it is 101 px, so the sprite stays visually sized ~0.76
         * of a cell while the extra margin reserves room for the rotation.
         */
        const val PIECE_TO_CELL_RATIO: Float = 115f / (1058f / 8f)

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
    // Board notation (chessboard-style A–H / 1–8 labels)
    // ─────────────────────────────────────────────────────────────────────
    object Notation {
        /** Toggle the notation labels around the board. */
        const val SHOW: Boolean = true

        /** Width of the rank-number strips (left/right) and height of the file-letter strips (top/bottom). */
        val STRIP_SIZE: Dp = 18.dp

        /** Gap between the labels and the board edge. */
        val GAP: Dp = 2.dp
    }

    // ─────────────────────────────────────────────────────────────────────
    // Online play
    // ─────────────────────────────────────────────────────────────────────
    object Online {
        /** Download page linked from the shared room-code message, for friends without the app. */
        const val DOWNLOAD_URL: String = "https://hlahoti.github.io/ReversiApp/"
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

        /** Strength of a `tap()` buzz (chip selects, back/pause taps, illegal moves). 0f–1f. */
        const val TAP_INTENSITY: Float = 1.0f

        /** Strength of each click in a `confirm()` double-pulse (legal moves, start, reset). 0f–1f. */
        const val CONFIRM_INTENSITY: Float = 1.0f

        /** Fallback one-shot duration + amplitude for the tap on Android 8–10. */
        const val FALLBACK_TAP_MS: Long = 14L

        /** Fallback one-shot for the confirm on Android 8–10 (and pre-O without amplitude). */
        const val FALLBACK_CONFIRM_MS: Long = 30L
        const val FALLBACK_CONFIRM_AMPLITUDE: Int = 180
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
