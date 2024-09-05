package tech.notifly.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotiflyUtilTest {
    @Test
    fun isValidProjectId() {
        assertTrue(NotiflyUtil.isValidProjectId("b80c3f0e2fbd5eb986df4f1d32ea2871"))
        assertFalse(NotiflyUtil.isValidProjectId("notifly"))
        assertFalse(NotiflyUtil.isValidProjectId("  b80c3f0e2fbd5eb986df4f1d32ea2871  "))
    }
}
