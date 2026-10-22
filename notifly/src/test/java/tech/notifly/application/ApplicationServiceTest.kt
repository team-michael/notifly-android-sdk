package tech.notifly.application

import android.app.Activity
import android.app.Application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ApplicationServiceTest {
    private lateinit var application: Application
    private lateinit var service: ApplicationService
    private val focusEvents = mutableListOf<Boolean>()

    @Before
    fun setup() {
        application = RuntimeEnvironment.getApplication()
        service = ApplicationService()
        service.addApplicationLifecycleHandler(
            object : BaseApplicationLifecycleHandler() {
                override fun onFocus(first: Boolean) {
                    focusEvents.add(first)
                }
            },
        )
        service.start(application)
    }

    @After
    fun tearDown() {
        application.unregisterActivityLifecycleCallbacks(service)
    }

    @Test
    fun `returning to the existing activity is a normal foreground transition`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        assertEquals(listOf(true), focusEvents)

        activity.pause().stop()
        activity.start().resume()

        assertEquals(listOf(true, false), focusEvents)
        activity.pause().stop().destroy()
    }

    @Test
    fun `new activity after all activities are destroyed starts a new session in the same process`() {
        val previousActivity = Robolectric.buildActivity(Activity::class.java).setup()
        previousActivity.pause().stop().destroy()

        val newActivity = Robolectric.buildActivity(Activity::class.java).setup()

        assertEquals(listOf(true, true), focusEvents)
        newActivity.pause().stop().destroy()
    }
}
