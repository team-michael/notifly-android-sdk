package tech.notifly

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tech.notifly.command.CommandDispatcher
import tech.notifly.command.models.CommandBase
import tech.notifly.command.models.SetUserPropertiesCommand
import tech.notifly.command.models.SetUserPropertiesPayload
import tech.notifly.utils.Logger

class NotiflyUserPropertyKeyWarningTest {
    private val context = mockk<Context>()
    private val warnings = mutableListOf<String>()
    private val commands = mutableListOf<CommandBase>()

    // Inspect the queued command without executing its asynchronous storage/network work.
    private fun forwardedParams(): Map<String, Any?> {
        val command = commands.last() as SetUserPropertiesCommand
        val field = SetUserPropertiesCommand::class.java.getDeclaredField("payload")
        field.isAccessible = true
        return (field.get(command) as SetUserPropertiesPayload).params
    }

    @Before
    fun setup() {
        mockkObject(Logger, CommandDispatcher)
        every { Logger.w(any(), any()) } answers {
            warnings.add(firstArg())
            Unit
        }
        every { CommandDispatcher.dispatch(any()) } answers {
            commands.add(firstArg())
            Unit
        }
    }

    @After
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `valid keys are dispatched without warnings`() {
        val params = mapOf("user_name" to "Kim", "purchase_count_30d" to 3)

        Notifly.setUserProperties(context, params)

        assertTrue(warnings.isEmpty())
        assertSame(params, forwardedParams())
    }

    @Test
    fun `mixed input warns once while preserving null and nested values`() {
        val params =
            mapOf(
                "valid_key" to "홍길동.$",
                "first.name" to null,
                "사용자명" to "private-value",
                "profile" to mapOf("nested.key" to "nested-value"),
            )
        Notifly.setUserProperties(context, params)
        assertEquals(1, warnings.size)
        val message = warnings.single()
        assertTrue(message.contains("[\"first.name\",\"사용자명\"]"))
        assertTrue(message.contains("A-Z, a-z"))
        assertTrue(message.contains("0-9"))
        assertTrue(message.contains("underscores (_)"))
        assertTrue(message.contains("does not block"))
        assertFalse(message.contains("private-value"))
        assertFalse(message.contains("홍길동"))
        assertFalse(message.contains("nested"))
        assertSame(params, forwardedParams())
    }

    @Test
    fun `object value keys and special characters in values do not warn`() {
        Notifly.setUserProperties(context, mapOf("name" to "홍길동.$", "profile" to mapOf("first.name" to "Kim")))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `repeated calls warn each time`() {
        repeat(2) { Notifly.setUserProperties(context, mapOf("first.name" to "Kim")) }
        assertEquals(2, warnings.size)
        assertEquals(2, commands.size)
    }

    @Test
    fun `invalid timezone recursion does not duplicate the key warning`() {
        val params = mapOf("\$timezone" to "Invalid/Timezone", "first.name" to "Kim")
        Notifly.setUserProperties(context, params)
        assertEquals(1, warnings.count { it.contains("custom user property key") })
        assertEquals(mapOf("first.name" to "Kim"), forwardedParams())
        assertEquals("Invalid/Timezone", params["\$timezone"])
    }

    @Test
    fun `convenience setters do not produce key warnings`() {
        Notifly.setEmail(context, "a@example.com")
        Notifly.setPhoneNumber(context, "01012345678")
        Notifly.setTimezone(context, "Asia/Seoul")
        assertTrue(warnings.isEmpty())
        assertEquals(3, commands.size)
    }

    @Test
    fun `warning escapes control characters and quotes`() {
        Notifly.setUserProperties(context, mapOf("name\n\"\t" to "secret"))
        val message = warnings.single()
        assertTrue(message.contains("[\"name\\n\\\"\\t\"]"))
        assertFalse(message.any { it == '\n' || it == '\r' || it == '\t' })
    }
}
