package com.lainsmain.mneme.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryKeyManagerTest {
    @Test
    fun recoveryCodeRoundTripsWithoutAmbiguity() {
        val key = ByteArray(32) { (it * 17).toByte() }
        val code = RecoveryKeyManager.formatCode(key)

        assertEquals(71, code.length)
        assertEquals(7, code.count { it == '-' })
        assertArrayEquals(key, RecoveryKeyManager.parseCode(code.lowercase()))
        assertArrayEquals(key, RecoveryKeyManager.parseCode(code.replace("-", " ")))
    }

    @Test
    fun incompleteRecoveryCodeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryKeyManager.parseCode("1234")
        }
    }
}
