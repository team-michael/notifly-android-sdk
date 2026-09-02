package tech.notifly.utils

import android.content.Context
import android.provider.Settings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotiflyAuthUtilTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, DEVICE_ID)

        mockkObject(NotiflyStorage)
        every { NotiflyStorage.get(context, NotiflyStorageItem.PROJECT_ID) } returns PROJECT_ID
        every { NotiflyStorage.get(context, NotiflyStorageItem.EXTERNAL_USER_ID) } returns null

        mockkObject(NotiflyFirebaseUtil)
    }

    @After
    fun tearDown() {
        unmockkObject(NotiflyFirebaseUtil)
        unmockkObject(NotiflyStorage)
    }

    @Test
    fun `anonymous user id remains device based regardless of FCM token`() =
        runTest {
            coEvery { NotiflyFirebaseUtil.getFcmToken() } returns null
            val userIdWithoutFcmToken = NotiflyAuthUtil.getNotiflyUserId(context)

            coEvery { NotiflyFirebaseUtil.getFcmToken() } returns "rotated-fcm-token"
            val userIdWithFcmToken = NotiflyAuthUtil.getNotiflyUserId(context)

            assertEquals(EXPECTED_ANONYMOUS_USER_ID, userIdWithoutFcmToken)
            assertEquals(EXPECTED_ANONYMOUS_USER_ID, userIdWithFcmToken)
        }

    companion object {
        private const val PROJECT_ID = "0123456789abcdef0123456789abcdef"
        private const val DEVICE_ID = "test-android-id"
        private const val EXPECTED_ANONYMOUS_USER_ID = "01eacc724a70562481f82d9efdfb9ea6"
    }
}
