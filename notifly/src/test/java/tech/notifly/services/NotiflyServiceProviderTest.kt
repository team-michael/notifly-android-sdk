import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tech.notifly.services.NotiflyServiceProvider.getService
import tech.notifly.services.NotiflyServiceProvider.register
import tech.notifly.services.NotiflyServiceProvider.unregister

class NotiflyServiceProviderTest {
    internal interface TestService {
        val serviceName: String?
    }

    internal class TestServiceImpl : TestService {
        override val serviceName: String?
            get() = "TestService"
    }

    @BeforeEach
    @Throws(Exception::class)
    fun setUp() {
        // Ensure the service registration map is clear before each test
        unregister(TestService::class.java)
    }

    @Test
    fun testServiceRegistrationAndRetrieval() {
        // Register the service
        val serviceInstance = TestServiceImpl()
        register(TestService::class.java, serviceInstance)

        // Retrieve the service
        val retrievedService =
            getService(
                TestService::class.java,
            )

        // Verify that the retrieved service is the same as the one registered
        assertNotNull(retrievedService, "Service should not be null")
        assertEquals(
            retrievedService.serviceName,
            "TestService",
            "Service names should match",
        )
    }

    @Test
    fun testServiceUnregistration() {
        // Register and then unregister the service
        val serviceInstance = TestServiceImpl()
        register(TestService::class.java, serviceInstance)
        unregister(TestService::class.java)

        // Attempt to retrieve the service after unregistration
        val retrievedService =
            getService(
                TestService::class.java,
            )

        // Verify that the service is no longer available
        assertNull(retrievedService, "Service should be null after unregistration")
    }
}
