package tech.notifly.kmp

import org.junit.Assert.assertNotNull
import org.junit.Test
import tech.notifly.kmp.identity.UserIdTransitionPolicy

class NotiflyKmpSdkSmokeTest {
    @Test
    fun `Notifly KMP SDK is linked and callable`() {
        val result = UserIdTransitionPolicy.evaluate(null, "connectivity-check")

        assertNotNull(result)
    }
}
