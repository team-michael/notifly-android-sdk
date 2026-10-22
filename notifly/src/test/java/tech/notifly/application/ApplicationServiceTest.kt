package tech.notifly.application

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
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
        createService()
        service.start(application)
    }

    private fun createService() {
        service = ApplicationService()
        service.addApplicationLifecycleHandler(
            object : BaseApplicationLifecycleHandler() {
                override fun onFocus(first: Boolean) {
                    focusEvents.add(first)
                }
            },
        )
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

    @Test
    fun `opening another activity while a stopped activity exists is a normal foreground transition`() {
        val previousActivity = Robolectric.buildActivity(Activity::class.java).setup()
        previousActivity.pause().stop()

        val newActivity = Robolectric.buildActivity(Activity::class.java).setup()

        assertEquals(listOf(true, false), focusEvents)
        newActivity.pause().stop().destroy()
        previousActivity.destroy()
    }

    @Test
    fun `initialization with an existing activity tracks it until destruction`() {
        application.unregisterActivityLifecycleCallbacks(service)
        val previousActivity = Robolectric.buildActivity(Activity::class.java).setup()
        createService()
        service.start(previousActivity.get())
        previousActivity.pause().stop()

        val nextActivity = Robolectric.buildActivity(Activity::class.java).setup()
        assertEquals(listOf(true, false), focusEvents)
        nextActivity.pause().stop().destroy()
        previousActivity.destroy()

        val restartedActivity = Robolectric.buildActivity(Activity::class.java).setup()
        assertEquals(listOf(true, false, true), focusEvents)
        restartedActivity.pause().stop().destroy()
    }

    @Test
    fun `activity first observed on resume is retained while stopped`() {
        application.unregisterActivityLifecycleCallbacks(service)
        val previousActivity = Robolectric.buildActivity(Activity::class.java).setup()
        createService()
        service.start(application)
        previousActivity.pause().resume()
        previousActivity.pause().stop()

        val nextActivity = Robolectric.buildActivity(Activity::class.java).setup()

        assertEquals(listOf(true, false), focusEvents)
        nextActivity.pause().stop().destroy()
        previousActivity.destroy()
    }

    @Test
    fun `restart after configuration recreation starts a new session`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val previousInstance = activity.get()
        val newConfiguration = Configuration(previousInstance.resources.configuration)
        newConfiguration.orientation =
            if (newConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Configuration.ORIENTATION_PORTRAIT
            } else {
                Configuration.ORIENTATION_LANDSCAPE
            }

        activity.configurationChange(newConfiguration)
        assertNotSame(previousInstance, activity.get())
        assertEquals(listOf(true), focusEvents)
        activity.pause().stop().destroy()
        assertFalse(service.isInForeground)

        val newActivity = Robolectric.buildActivity(Activity::class.java).setup()

        assertEquals(listOf(true, true), focusEvents)
        newActivity.pause().stop().destroy()
    }
}
