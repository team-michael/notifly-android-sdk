package tech.notifly.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class NotiflyUtilTest {
    @org.junit.jupiter.api.Test
    fun isValidProjectId() {
        assertTrue(NotiflyUtil.isValidProjectId("b80c3f0e2fbd5eb986df4f1d32ea2871"))
        assertFalse(NotiflyUtil.isValidProjectId("notifly"))
        assertFalse(NotiflyUtil.isValidProjectId("  b80c3f0e2fbd5eb986df4f1d32ea2871  "))
    }
}
