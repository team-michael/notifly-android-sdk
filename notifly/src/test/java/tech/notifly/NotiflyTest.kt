import android.content.Context
import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import tech.notifly.Notifly
import tech.notifly.sdk.NotiflySdkState
import tech.notifly.sdk.NotiflySdkStateManager
import tech.notifly.utils.N
import tech.notifly.utils.NotiflyUserUtil

class NotiflyTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk<Context>()
        setupSharedPreferences()
    }

    private fun setupSharedPreferences() {
        val sharedPreferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { context.getSharedPreferences("NotiflyAndroidSDKPlainStorage", 0) } returns sharedPreferences
        every { sharedPreferences.getString(any(), any()) } returns null
        every { sharedPreferences.edit() } returns editor
        setupSharedPreferencesEditor(editor)
    }

    private fun setupSharedPreferencesEditor(editor: SharedPreferences.Editor) {
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.putFloat(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.clear() } returns editor
        every { editor.apply() } just Runs
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `ensure setUserId and setUserProperties execute in order`() = runTest {
        // Given
        mockkObject(NotiflySdkStateManager)
        every { NotiflySdkStateManager.getState() } returns NotiflySdkState.READY
        coEvery { NotiflySdkStateManager.setState(any()) } answers { callOriginal() }

        mockkObject(NotiflyUserUtil)
        coEvery { NotiflyUserUtil.setUserProperties(any(), any()) } returns Unit

        // When
        Notifly.setUserId(context, "newUserId")
        Notifly.setUserProperties(context, mapOf("key" to "value"))

        // Then
        coVerifyOrder {
            NotiflyUserUtil.setUserProperties(context, mapOf(N.KEY_EXTERNAL_USER_ID to "newUserId"))
            NotiflyUserUtil.setUserProperties(context, mapOf("key" to "value"))
        }
    }
}
