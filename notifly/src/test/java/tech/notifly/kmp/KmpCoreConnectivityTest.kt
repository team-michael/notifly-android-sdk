package tech.notifly.kmp

import org.junit.Assert.assertNotNull
import org.junit.Test
import tech.notifly.kmp.identity.UserIdTransitionPolicy

class KmpCoreConnectivityTest {
    @Test
    fun `KMP core is linked and callable`() {
        val result = UserIdTransitionPolicy.evaluate(null, "connectivity-check")

        assertNotNull(result)
    }
}
