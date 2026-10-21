package com.example.reversi.config

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.reversi.bot.MoveSelection
import com.example.reversi.bot.Opponent

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

        /**
         * Master switch for move variety. false = every bot always plays its
         * single best move, so the same human moves get the same replies.
         */
        const val VARIETY_ENABLED: Boolean = true

        /**
         * Fixed RNG seed, for reproducing a bot's games while debugging.
         * null = a fresh random sequence every time a bot is loaded.
         */
        val VARIETY_SEED: Long? = null

        /**
         * Per-opponent move variety (see [MoveSelection] for what each field
         * does). Scores are tanh-scaled evaluations, but each model uses the
         * range differently: in the opening, the gap between the best and
         * second-best root move has a median of ~0.06–0.12 for the MLP bots
         * and only ~0.02 for the CNN bots, hence the much smaller CNN values.
         *  - margin: worst a varied pick may be vs. the best move.
         *  - temperature: softmax spread; higher = closer to uniform.
         *  - variedPlies / taperPlies: full variety for the first N plies
         *    (discs placed), then fades to best-move play over the next M.
         * Weaker bots get more variety, stronger bots less. An opponent missing
         * from this map plays deterministically.
         */
        val VARIETY: Map<Opponent, MoveSelection> = mapOf(
            Opponent.VISHY  to MoveSelection(margin = 0.30f, temperature = 0.10f,  variedPlies = 20, taperPlies = 12),
            Opponent.GUK    to MoveSelection(margin = 0.20f, temperature = 0.07f,  variedPlies = 16, taperPlies = 10),
            Opponent.ARJUN  to MoveSelection(margin = 0.15f, temperature = 0.05f,  variedPlies = 14, taperPlies = 8),
            Opponent.SHADOW to MoveSelection(margin = 0.05f, temperature = 0.02f,  variedPlies = 12, taperPlies = 8),
            Opponent.PRAGG  to MoveSelection(margin = 0.05f, temperature = 0.02f,  variedPlies = 12, taperPlies = 8),
            Opponent.KONERU to MoveSelection(margin = 0.04f, temperature = 0.015f, variedPlies = 10, taperPlies = 6),
        )

        /** Floor on how long the "Thinking…" state stays visible, in ms. */
        const val THINKING_MIN_MS: Long = 150L

        /** Delay before auto-passing a side with no legal moves, in ms. */
        const val AUTO_PASS_DELAY_MS: Long = 500L
    }

    // ─────────────────────────────────────────────────────────────────────
    // Learning (bots that improve from their games, e.g. Shadow)
    // ─────────────────────────────────────────────────────────────────────
    object Learning {
        /**
         * How far into a game the opening book steers, in plies (discs
         * placed). Before this, a learning bot looks for alternatives to
         * every line it has played and avoids repeating its losses. After
         * it, the bot just searches. Higher = longer memory, but each
         * finished game costs up to this many extra searches to learn.
         */
        const val BOOK_HORIZON_PLIES: Int = 30
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
    // Replaying a finished game from the history
    // ─────────────────────────────────────────────────────────────────────
    object Replay {
        /** Pause between plies when a replay plays itself, after each move's flips settle. */
        const val STEP_MS: Long = 700L
    }

    // ─────────────────────────────────────────────────────────────────────
    // Endgame review of a replayed game (com.example.reversi.analysis)
    // ─────────────────────────────────────────────────────────────────────
    object Analysis {
        /**
         * Moves played with this many squares empty or fewer are solved
         * exactly and graded. Each extra empty roughly doubles to triples
         * the solving time.
         */
        const val MAX_EMPTIES: Int = 14

        /**
         * How much a final margin of `d` discs is worth: `tanh(d / this)`,
         * from -1 (a crushing loss) to 1. Moves are graded by how much of
         * that they give away, so a few discs matter in a close game and
         * hardly at all once it's decided.
         */
        const val STAKE_SCALE_DISCS: Float = 16f

        /** Worth given away, against the best move, that makes a mistake. */
        const val MISTAKE_STAKE: Float = 0.25f

        /** Worth given away, against the best move, that makes a blunder. */
        const val BLUNDER_STAKE: Float = 0.7f
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

        /** The hinted dot pulses between these multiples of the hint-dot diameter. */
        const val HINT_PULSE_MIN_SCALE: Float = 1f
        const val HINT_PULSE_MAX_SCALE: Float = 2.6f

        /** One grow (or shrink) of the hint pulse, in ms. */
        const val HINT_PULSE_MS: Int = 550

        /** Last-move ring diameter as a fraction of cell width. */
        const val LAST_MOVE_RING_DIAMETER_RATIO: Float = 0.82f

        /** Last-move ring stroke width (px, not dp). */
        const val LAST_MOVE_RING_STROKE_PX: Float = 5f

        /** Last-move ring opacity (0–1). */
        const val LAST_MOVE_RING_ALPHA: Float = 0.85f

        /** Endgame-review badge ("?" / "??") diameter as a fraction of a cell. */
        const val BADGE_TO_CELL_RATIO: Float = 0.45f

        /** Opacity of the faint disc marking the better move in a review. */
        const val GHOST_ALPHA: Float = 0.6f

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
        /**
         * Download page linked from the shared room-code message, for friends
         * without the app, and opened by the Update button when a new version is out.
         */
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

        // Endgame-review badges.
        val Mistake     = Color(0xFFF0A040)
        val Blunder     = Color(0xFFE5534B)
    }
}
