package io.github.yasarmahomedabbas.paperlesscompanion.data

import io.github.yasarmahomedabbas.paperlesscompanion.data.update.sameSigners
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkSignerTest {
    private val ours = "our certificate".toByteArray()
    private val other = "someone else's certificate".toByteArray()

    @Test
    fun `the same certificate matches`() {
        assertTrue(sameSigners(listOf(ours), listOf(ours.copyOf())))
    }

    @Test
    fun `another certificate doesn't`() {
        assertFalse(sameSigners(listOf(ours), listOf(other)))
    }

    @Test
    fun `an unsigned APK never matches`() {
        assertFalse(sameSigners(listOf(ours), emptyList()))
    }

    @Test
    fun `no certificates of our own never match`() {
        assertFalse(sameSigners(emptyList(), listOf(ours)))
        assertFalse(sameSigners(emptyList(), emptyList()))
    }

    @Test
    fun `an extra signer doesn't match`() {
        assertFalse(sameSigners(listOf(ours), listOf(ours, other)))
    }

    @Test
    fun `the order of the certificates doesn't matter`() {
        assertTrue(sameSigners(listOf(ours, other), listOf(other, ours)))
    }
}
