package dev.axkit.app.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import dev.axkit.core.KnownHosts
import dev.axkit.core.SSHHost
import dev.axkit.core.SSHKeyFormat
import dev.axkit.core.SSHWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.Ed25519KeyFactory
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import java.io.OutputStream
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.util.Base64
import java.util.concurrent.Executors

/**
 * Android ships a cut-down "BC" provider; sshj needs the full BouncyCastle one for Ed25519 and curve25519. The full one
 * goes last in the list so every other part of the app keeps its usual providers.
 */
internal object SSHCrypto {
    @Volatile private var installed = false
    @Synchronized fun install() {
        if (installed) return
        if (Security.getProvider("BC") !is BouncyCastleProvider) {
            Security.removeProvider("BC")
            Security.addProvider(BouncyCastleProvider())
        }
        SecurityUtils.setSecurityProvider("BC")
        installed = true
    }
}

/** Checks the Mac's host key: remember it the first time, refuse it if it changes later. */
internal class HostKeyCheck(private val host: String, private val known: KnownHosts) : HostKeyVerifier {
    sealed interface Seen {
        data class FirstTime(val fingerprint: String) : Seen
        data class Changed(val new: String, val old: String) : Seen
    }

    @Volatile var seen: Seen? = null
        private set

    override fun verify(hostname: String?, port: Int, key: PublicKey): Boolean {
        val line = KeyType.fromKey(key).toString() + " " + Base64.getEncoder().encodeToString(Buffer.PlainBuffer().putPublicKey(key).compactData)
        return when (val check = known.check(line, host)) {
            KnownHosts.Check.Same -> true
            KnownHosts.Check.New -> {
                known.remember(line, host)
                seen = Seen.FirstTime(SSHKeyFormat.fingerprint(line) ?: "")
                true
            }
            is KnownHosts.Check.Changed -> {
                seen = Seen.Changed(new = line, old = check.previous)
                false
            }
        }
    }

    override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> =
        known.saved(host)?.split(" ")?.firstOrNull()?.let { listOf(it) } ?: emptyList()
}

/** The phone's Ed25519 key for sshj, made from the 32 bytes kept by [SSHKeyStore]. */
internal class Ed25519Provider(seed: ByteArray) : KeyProvider {
    private val private: PrivateKey = Ed25519KeyFactory.getPrivateKey(seed)
    private val public: PublicKey = Ed25519KeyFactory.getPublicKey(SSHKeyFormat.publicKey(seed))
    override fun getPrivate() = private
    override fun getPublic() = public
    override fun getType() = KeyType.ED25519
}

/** One terminal: an SSH shell on a Mac, drawn by ConnectBot's termlib. */
class TerminalSession(val host: SSHHost, private val store: TerminalStore) {
    sealed interface State {
        data object Idle : State
        data object Connecting : State
        data object Connected : State
        data class Ended(val message: String) : State
        data class HostKeyChanged(val newFingerprint: String, val newKey: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set
    /** Said once in the terminal on the first connection to a Mac. */
    var firstTimeFingerprint by mutableStateOf<String?>(null)
        private set
    /** Left because the app went to the background; come back and it reconnects by itself. */
    var leftForBackground by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Keys typed on the phone go out in order, one at a time. */
    private val writes = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var job: Job? = null
    private var client: SSHClient? = null
    private var shell: Session.Shell? = null
    private var stdin: OutputStream? = null
    private var generation = 0
    private var closed = false
    private var saidFirstTime = false

    val emulator: TerminalEmulator = TerminalEmulatorFactory.create(
        initialRows = 24, initialCols = 80,
        defaultForeground = Color(0xFFEBEBEB), defaultBackground = Color.Black,
        onKeyboardInput = { bytes -> send(bytes) },
        onResize = { d -> resize(d.columns, d.rows) },
        onClipboardCopy = { text -> onCopy?.invoke(text) },
    )

    /** Where OSC 52 copies from the Mac go (the phone's clipboard). */
    var onCopy: ((String) -> Unit)? = null

    val isLive: Boolean get() = state == State.Connected || state == State.Connecting

    fun connect() {
        if (isLive || closed) return
        leftForBackground = false
        state = State.Connecting
        val run = ++generation
        val check = HostKeyCheck(host.knownHostsKey, store.known)
        job = scope.launch {
            var opened: SSHClient? = null
            try {
                withContext(Dispatchers.IO) {
                    SSHCrypto.install()
                    val key = Ed25519Provider(store.privateKey())
                    val ssh = SSHClient(DefaultConfig())
                    opened = ssh
                    ssh.connectTimeout = 12_000
                    ssh.addHostKeyVerifier(check)
                    withContext(Dispatchers.Main) { if (run == generation) client = ssh }
                    ssh.connect(host.address, host.port)
                    ssh.authPublickey(host.user, key)
                    val session = ssh.startSession()
                    val size = emulator.dimensions
                    session.allocatePTY("xterm-256color", size.columns.coerceAtLeast(1), size.rows.coerceAtLeast(1), 0, 0, emptyMap())
                    val sh = session.startShell()
                    withContext(Dispatchers.Main) {
                        if (run != generation) return@withContext
                        shell = sh
                        stdin = sh.outputStream
                        state = State.Connected
                        // The screen may have measured itself while the shell was starting.
                        emulator.dimensions.let { d -> if (d.columns != size.columns || d.rows != size.rows) resize(d.columns, d.rows) }
                        (check.seen as? HostKeyCheck.Seen.FirstTime)?.let { first ->
                            firstTimeFingerprint = first.fingerprint
                            if (!saidFirstTime) {
                                saidFirstTime = true
                                feed("\u001B[2m" + SSHWords.firstTime(first.fingerprint) + "\u001B[0m\r\n")
                            }
                        }
                    }
                    val input = sh.inputStream
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n > 0 && run == generation && !closed) emulator.writeInput(buffer, 0, n)
                    }
                }
                if (run == generation) finish(if (leftForBackground) SSHWords.leftForBackground else SSHWords.ended)
            } catch (e: Throwable) {
                if (run != generation) return@launch
                val changed = check.seen as? HostKeyCheck.Seen.Changed
                when {
                    changed != null -> {
                        cleanUp()
                        state = State.HostKeyChanged(SSHKeyFormat.fingerprint(changed.new) ?: "", changed.new)
                    }
                    e is kotlinx.coroutines.CancellationException || leftForBackground ->
                        finish(if (leftForBackground) SSHWords.leftForBackground else SSHWords.disconnected)
                    else -> finish(SSHWords.failure(e, host.name))
                }
            } finally {
                // A run that was hung up or replaced must not leave its connection open.
                if (run != generation) opened?.let { c -> Thread { runCatching { c.disconnect() } }.start() }
            }
        }
    }

    fun disconnect(forBackground: Boolean = false) {
        leftForBackground = forBackground
        val wasLive = isLive || state is State.HostKeyChanged
        generation++
        closeClient()
        job = null
        if (wasLive) finish(if (forBackground) SSHWords.leftForBackground else SSHWords.disconnected)
    }

    /** The owner checked and the Mac really changed (reinstalled, new disk): trust the new key and connect. */
    fun trustNewKey(line: String) {
        store.known.remember(line, host.knownHostsKey)
        state = State.Idle
        connect()
    }

    fun send(bytes: ByteArray) {
        val out = stdin ?: return
        scope.launch(writes) { runCatching { out.write(bytes); out.flush() } }
    }

    fun resize(cols: Int, rows: Int) {
        val sh = shell ?: return
        if (cols <= 0 || rows <= 0) return
        scope.launch(writes) { runCatching { sh.changeWindowDimensions(cols, rows, 0, 0) } }
    }

    /** Words of our own, written into the terminal (dim). */
    private fun feed(text: String) {
        if (closed) return
        val bytes = text.toByteArray(Charsets.UTF_8)
        runCatching { emulator.writeInput(bytes, 0, bytes.size) }
    }

    /** Leaving the screen: hang up and free the terminal. */
    fun close() {
        disconnect()
        closed = true
        scope.cancel()
        writes.close()
        runCatching { emulator.close() }
    }

    private fun closeClient() {
        val c = client
        client = null
        if (c != null) Thread { runCatching { c.disconnect() } }.start()
    }

    private fun finish(message: String) {
        cleanUp()
        state = State.Ended(message)
    }

    private fun cleanUp() {
        closeClient()
        stdin = null
        shell = null
        job = null
    }
}
