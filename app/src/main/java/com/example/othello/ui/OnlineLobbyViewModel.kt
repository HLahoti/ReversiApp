package com.example.othello.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.othello.game.Player
import com.example.othello.net.OnlineClient
import com.example.othello.net.OnlineError
import com.example.othello.net.OnlineRegistry
import com.example.othello.net.OnlineScope
import com.example.othello.net.OnlineSession
import com.example.othello.net.RoomCode
import com.example.othello.net.RoomCodes
import com.example.othello.net.SessionHandoff
import com.example.othello.net.SessionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which page the lobby UI is showing. */
enum class LobbyStep { PICK, CREATING, WAITING, JOINING }

/**
 * UI state for [OnlineLobbyScreen]. Keeping Compose state in the view-model
 * means the lobby survives configuration changes (rotation) without dropping
 * an in-flight room.
 */
data class LobbyState(
    val step: LobbyStep = LobbyStep.PICK,
    val roomCode: RoomCode? = null,
    val error: String? = null,
    /** Token to pass to the game screen when the room is ACTIVE and ready. */
    val handoffToken: String? = null,
)

class OnlineLobbyViewModel(app: Application) : AndroidViewModel(app) {

    // Resolved from the process-wide registry so Android's default
    // ViewModel factory (which can only hand us the Application) still works.
    private val client: OnlineClient = OnlineRegistry.current

    private val _state = MutableStateFlow(LobbyState())
    val state: StateFlow<LobbyState> = _state.asStateFlow()

    private var session: OnlineSession? = null
    private var sessionJob: Job? = null

    fun createRoom(hostColor: Player?) {
        if (_state.value.step != LobbyStep.PICK) return
        _state.update { it.copy(step = LobbyStep.CREATING, error = null) }
        viewModelScope.launch {
            try {
                val s = client.createRoom(hostColor)
                session = s
                _state.update { it.copy(step = LobbyStep.WAITING, roomCode = s.roomCode) }
                watchForActive(s)
            } catch (t: Throwable) {
                _state.update { it.copy(step = LobbyStep.PICK, error = t.humanMessage()) }
            }
        }
    }

    fun joinRoom(codeInput: String) {
        if (_state.value.step != LobbyStep.PICK) return
        val code = RoomCodes.parse(codeInput)
        if (code == null) {
            _state.update { it.copy(error = "Code must be ${RoomCodes.LENGTH} letters/digits.") }
            return
        }
        _state.update { it.copy(step = LobbyStep.JOINING, error = null, roomCode = code) }
        viewModelScope.launch {
            try {
                val s = client.joinRoom(code)
                session = s
                watchForActive(s)
            } catch (t: Throwable) {
                _state.update { it.copy(step = LobbyStep.PICK, error = t.humanMessage()) }
            }
        }
    }

    /** Cancel whatever is pending and return to the picker. */
    fun cancel() {
        sessionJob?.cancel()
        sessionJob = null
        session?.let { s ->
            // OnlineScope so a cancel-and-navigate-away sequence can't abort
            // the finish mid-flight.
            OnlineScope.launch { runCatching { s.finish() } }
        }
        session = null
        _state.value = LobbyState()
    }

    /** The game screen has taken the session; forget it here so onCleared doesn't close it. */
    fun markHandoffConsumed() {
        session = null
        sessionJob?.cancel()
        sessionJob = null
        _state.update { it.copy(handoffToken = null) }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        val tok = _state.value.handoffToken
        if (tok != null) {
            // We parked but never navigated — drop it so Firestore listeners stop.
            SessionHandoff.discard(tok)
        } else {
            session?.close()
        }
        super.onCleared()
    }

    private fun watchForActive(session: OnlineSession) {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            session.state.collectLatest { snap ->
                when (snap.status) {
                    SessionStatus.WAITING -> Unit // keep waiting
                    SessionStatus.ACTIVE -> {
                        val color = snap.myColor ?: return@collectLatest
                        val token = SessionHandoff.park(session, color)
                        _state.update { it.copy(handoffToken = token) }
                    }
                    SessionStatus.ABANDONED, SessionStatus.FINISHED -> {
                        _state.update {
                            it.copy(
                                step = LobbyStep.PICK,
                                error = snap.error ?: "Room was closed.",
                                roomCode = null,
                            )
                        }
                        session.close()
                        this@OnlineLobbyViewModel.session = null
                    }
                }
            }
        }
    }

    private fun Throwable.humanMessage(): String = when (this) {
        is OnlineError.RoomNotFound -> "No room with that code."
        is OnlineError.RoomFull -> "That room already has two players."
        is OnlineError.InvalidCode -> "That code doesn't look right."
        is OnlineError.Network -> "Network trouble — check your connection."
        is OnlineError.NotSignedIn -> "Could not sign in to the online service."
        is OnlineError -> message ?: "Something went wrong."
        else -> message ?: this::class.java.simpleName
    }
}
