package tech.notifly.kmp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.notifly.command.models.normalizeUserId
import tech.notifly.kmp.identity.UserIdTransitionPolicy

class UserIdTransitionPolicyIntegrationTest {
    @Test
    fun `anonymous to identified requests sync and merge`() {
        val decision = UserIdTransitionPolicy.evaluate(null, "user-a")

        assertTrue(decision.changed)
        assertTrue(decision.shouldSync)
        assertTrue(decision.shouldMerge)
        assertFalse(decision.shouldClear)
    }

    @Test
    fun `identified to anonymous requests sync and clear`() {
        val decision = UserIdTransitionPolicy.evaluate("user-a", null)

        assertTrue(decision.changed)
        assertTrue(decision.shouldSync)
        assertFalse(decision.shouldMerge)
        assertTrue(decision.shouldClear)
    }

    @Test
    fun `empty new user ID preserves Android anonymous semantics`() {
        val decision = UserIdTransitionPolicy.evaluate(normalizeUserId(null), normalizeUserId(""))

        assertFalse(decision.changed)
        assertFalse(decision.shouldSync)
        assertFalse(decision.shouldMerge)
        assertTrue(decision.shouldClear)
    }

    @Test
    fun `stored empty user ID merges when Android identifies the user`() {
        val decision = UserIdTransitionPolicy.evaluate(normalizeUserId(""), normalizeUserId("user-a"))

        assertTrue(decision.changed)
        assertTrue(decision.shouldSync)
        assertTrue(decision.shouldMerge)
        assertFalse(decision.shouldClear)
    }
}
