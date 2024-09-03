package tech.notifly.http.impl

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tech.notifly.http.HttpClientOptions

class HttpClientTest {
    @BeforeEach
    fun setUp() {
        // Do not log anything because JUnit tests do not have a android logger
        tech
            .notifly
            .utils
            .Logger
            .setLogLevel(7)
    }

    @Test
    fun `timeout request will give a bad response`() =
        runBlocking {
            // Given
            val mockResponse = MockHttpConnectionFactory.MockResponse()
            val mockHttpClientOptions = HttpClientOptions(200, 200)
            mockResponse.mockRequestTime = 10000

            val factory = MockHttpConnectionFactory(mockResponse)
            val httpClient = HttpClient(factory, mockHttpClientOptions)

            // When
            val response = httpClient.get("https://api.notifly.tech/mocking/timeout", null)

            // Then
            assertEquals(0, response.statusCode)
            assertNotNull(response.throwable)
            assertTrue(response.throwable is TimeoutCancellationException)
        }

    @Test
    fun `Error response`() =
        runBlocking {
            // Given
            val payload = "ERROR RESPONSE"
            val mockResponse =
                MockHttpConnectionFactory.MockResponse().apply {
                    status = 400
                    errorResponseBody = payload
                }

            val factory = MockHttpConnectionFactory(mockResponse)
            val httpClient = HttpClient(factory, HttpClientOptions(200, 200))

            // When
            val response = httpClient.post("https://api.notifly.tech/mocking/error", JSONObject(), null)

            // Then
            assertEquals(400, response.statusCode)
            assertEquals(payload, response.payload)
        }
}
