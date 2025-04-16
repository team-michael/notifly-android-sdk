import android.content.Context
import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import tech.notifly.command.models.SetUserIdCommand
import tech.notifly.command.models.SetUserIdPayload
import tech.notifly.sdk.NotiflySdkState
import tech.notifly.sdk.NotiflySdkStateManager
import tech.notifly.utils.NotiflyUserUtil

class CommandsTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk<Context>()
        setupSharedPreferences()
        setupLog()

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

    private fun setupLog() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `execute should call setState READY on success`() =
        runTest {
            // Given
            val payload = SetUserIdPayload(context, "newUserId")
            val command = SetUserIdCommand(payload)

            mockkObject(NotiflyUserUtil)
            coEvery { NotiflyUserUtil.setUserProperties(any(), any()) } returns Unit

            mockkObject(NotiflySdkStateManager)
            coEvery { NotiflySdkStateManager.setState(any()) } answers { callOriginal() }

            // When
            command.execute()

            // Then
            verify(exactly = 1) { NotiflySdkStateManager.setState(NotiflySdkState.READY) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `execute should call setState FAILED on failure`() =
        runTest {
            // Given
            val payload = SetUserIdPayload(context, "newUserId2")
            val command = SetUserIdCommand(payload)

            mockkObject(NotiflyUserUtil)
            coEvery { NotiflyUserUtil.setUserProperties(any(), any()) } throws Exception()

            mockkObject(NotiflySdkStateManager)
            coEvery { NotiflySdkStateManager.setState(any()) } answers { callOriginal() }

            // When
            command.execute()

            // Then
            verify(exactly = 1) { NotiflySdkStateManager.setState(NotiflySdkState.FAILED) }
        }
}
