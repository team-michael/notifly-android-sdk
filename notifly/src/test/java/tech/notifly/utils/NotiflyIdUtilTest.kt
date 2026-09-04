package tech.notifly.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotiflyIdUtilTest {
    @Test
    fun `event IDs are unique UUID v4 values without hyphens`() {
        val ids = List(1_000) { NotiflyIdUtil.generateEventId() }

        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertTrue(id.matches(Regex("^[0-9a-f]{32}$")))
            assertEquals('4', id[12])
            assertTrue(id[16] in setOf('8', '9', 'a', 'b'))
        }
    }

    @Test
    fun `deterministic IDs retain existing UUID v5 fixtures`() {
        assertEquals(
            "9f643d2144ab59d59ca9e146249fb68f",
            NotiflyIdUtil.generate(
                NotiflyIdUtil.Namespace.NAMESPACE_REGISTERED_USER_ID,
                "0123456789abcdef0123456789abcdeftest-external-user-id",
            ),
        )
        assertEquals(
            "01eacc724a70562481f82d9efdfb9ea6",
            NotiflyIdUtil.generate(
                NotiflyIdUtil.Namespace.NAMESPACE_UNREGISTERED_USER_ID,
                "0123456789abcdef0123456789abcdeftest-android-id",
            ),
        )
        assertEquals(
            "5ce9b25722a054d7a818da7b9e2fc41d",
            NotiflyIdUtil.generate(
                NotiflyIdUtil.Namespace.NAMESPACE_DEVICE_ID,
                "test-android-id",
            ),
        )
    }
}
