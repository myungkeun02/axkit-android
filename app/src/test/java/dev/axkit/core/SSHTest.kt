package dev.axkit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SSHTest {
    private class MapStore : SSHStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun set(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    @Test fun presetsKeysAndKnownHosts() {
        val presets = SSHHost.presets("서버 맥=alice@100.1.2.3, 노트북=bob@100.4.5.6:2222, 이상한 것, =x@y")
        assertEquals(listOf("서버 맥", "노트북"), presets.map { it.name })
        assertEquals(2222, presets[1].port)
        assertEquals("[100.4.5.6]:2222", presets[1].knownHostsKey)
        assertEquals("alice@100.1.2.3", presets[0].target)
        assertEquals("100.1.2.3", presets[0].knownHostsKey)
        assertTrue(SSHHost.presets("$(AX_SSH_HOSTS)").isEmpty())
        assertTrue(SSHHost.presets(null).isEmpty())
        assertTrue(SSHHost.presets("").isEmpty())

        // A fixed key: the line and fingerprint must match what ssh-keygen prints for it (same values as the iOS test).
        val seed = ByteArray(32) { 7 }
        val line = SSHKeyFormat.openSSHLine(SSHKeyFormat.publicKey(seed), comment = "ax-kit iPhone")
        assertEquals("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOpKbGPinFIKvvVQexMuxfmVR3auvr57kkIe6mkURtIs ax-kit iPhone", line)
        assertEquals("SHA256:z/fSv0Z0RZS+Lccbc6ZoOWwt/fbj1VFJGSBKQbo4icE", SSHKeyFormat.fingerprint(line))
        assertEquals(51, SSHKeyFormat.blob(SSHKeyFormat.publicKey(seed)).size)
        assertNull(SSHKeyFormat.fingerprint("ssh-ed25519"))
        assertNull(SSHKeyFormat.fingerprint("ssh-ed25519 !!!notbase64"))

        val store = MapStore()
        val known = KnownHosts(store)
        assertEquals(KnownHosts.Check.New, known.check("ssh-ed25519 AAA1", "h"))
        known.remember("ssh-ed25519 AAA1 old-comment", "h")
        assertEquals(KnownHosts.Check.Same, known.check("ssh-ed25519 AAA1", "h"))
        assertEquals(KnownHosts.Check.Changed("ssh-ed25519 AAA1 old-comment"), known.check("ssh-ed25519 BBB2", "h"))
        known.forget("h")
        assertEquals(KnownHosts.Check.New, known.check("ssh-ed25519 BBB2", "h"))

        val list = SSHHostList(store)
        list.seed(presets)
        assertEquals(2, list.hosts.size)
        list.save(list.hosts.drop(1))   // the owner deletes one
        list.seed(presets)              // next launch
        assertEquals("a deleted preset does not come back", listOf("노트북"), list.hosts.map { it.name })
    }

    @Test fun parseTargets() {
        assertNull(SSHHost.parse("nouser", "x"))
        assertNull(SSHHost.parse("@host", "x"))
        assertNull(SSHHost.parse("me@", "x"))
        assertNull(SSHHost.parse("a b@host", "x"))
        assertNull(SSHHost.parse("me@host:0", "x"))
        assertNull(SSHHost.parse("me@host:70000", "x"))
        assertNull(SSHHost.parse("me@host:ssh", "x"))
        val bracket = SSHHost.parse("me@[mac.tail]", "x")
        assertEquals("mac.tail", bracket?.address)
        val v6 = SSHHost.parse("me@fd7a::1", "x")
        assertEquals("fd7a::1", v6?.address)
        assertEquals(22, v6?.port)
        assertEquals("preset:me@mac:2200", SSHHost.parse("me@mac:2200", "x")?.id)
    }

    @Test fun hostSheetValidation() {
        assertNull(SSHHost.built(null, " ", "me", "100.1.2.3", "22"))
        assertNull(SSHHost.built(null, "맥", "m e", "100.1.2.3", "22"))
        assertNull(SSHHost.built(null, "맥", "me", "", "22"))
        assertNull(SSHHost.built(null, "맥", "me", "100.1.2.3", "0"))
        assertNull(SSHHost.built(null, "맥", "me", "100.1.2.3", ""))
        val made = SSHHost.built(null, " 서버 맥 ", " me ", " 100.1.2.3 ", "2222")
        assertNotNull(made)
        assertEquals("서버 맥", made!!.name)
        assertEquals("me@100.1.2.3:2222", made.target)
        assertTrue(made.id.startsWith("h_") && made.id.length == 10)
        assertEquals("h_keep", SSHHost.built("h_keep", "맥", "me", "mac", "22")?.id)
    }

    @Test fun plainWordsForFailures() {
        assertTrue(SSHWords.failure(SocketTimeoutException("connect timed out"), "맥").startsWith("맥에 닿지 않아요"))
        assertTrue(SSHWords.failure(ConnectException("Connection refused"), "맥").contains("원격 로그인"))
        assertTrue(SSHWords.failure(UnknownHostException("mac.tail"), "맥").startsWith("맥을(를) 찾지 못했어요"))
        assertTrue(SSHWords.failure(RuntimeException("Exhausted available authentication methods"), "맥").contains("authorized_keys"))
        assertEquals("맥에 연결하지 못했어요. 잠시 뒤 다시 해 주세요.", SSHWords.failure(IllegalStateException("boom"), "맥"))
    }
}
