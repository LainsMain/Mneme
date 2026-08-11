package com.lainsmain.mneme.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {
    @Test
    fun comparesStableVersions() {
        assertTrue(SemanticVersion.isNewer("v1.2.0", "1.1.9"))
        assertFalse(SemanticVersion.isNewer("1.2.0", "1.2.0"))
        assertFalse(SemanticVersion.isNewer("1.1.9", "1.2.0"))
    }

    @Test
    fun stableReleaseSupersedesPrerelease() {
        assertTrue(SemanticVersion.isNewer("0.1.0", "0.1.0-dev"))
        assertFalse(SemanticVersion.isNewer("0.1.0-dev", "0.1.0"))
    }
}
