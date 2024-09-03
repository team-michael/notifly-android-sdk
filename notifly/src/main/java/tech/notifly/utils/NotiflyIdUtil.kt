package tech.notifly.utils

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * ID Generator for Notifly based on UUID v5 with namespaces, omitting "-" for simplicity
 */
internal object NotiflyIdUtil {
    enum class Namespace(
        val uuid: UUID,
    ) {
        NAMESPACE_EVENT_ID(UUID.fromString("830b5f7b-e392-43db-a17b-d835f0bcab2b")),
        NAMESPACE_REGISTERED_USER_ID(UUID.fromString("ce7c62f9-e8ae-4009-8fd6-468e9581fa21")),
        NAMESPACE_UNREGISTERED_USER_ID(UUID.fromString("a6446dcf-c057-4de7-a360-56af8659d52f")),
        NAMESPACE_DEVICE_ID(UUID.fromString("830848b3-2444-467d-9cd8-3430d2738c57")),
    }

    private fun generateUUIDv5(
        namespace: Namespace,
        name: String,
    ): UUID {
        val namespaceBytes =
            ByteBuffer
                .wrap(ByteArray(16))
                .putLong(namespace.uuid.mostSignificantBits)
                .putLong(namespace.uuid.leastSignificantBits)
                .array()

        val nameBytes = name.toByteArray(Charsets.UTF_8)

        val inputData = namespaceBytes + nameBytes

        val digest = MessageDigest.getInstance("SHA-1")
        val uuidBytes = digest.digest(inputData).copyOfRange(0, 16)

        uuidBytes[6] = (uuidBytes[6].toInt() and 0x0F).toByte()
        uuidBytes[6] = (uuidBytes[6].toInt() or (5 shl 4)).toByte()

        uuidBytes[8] = (uuidBytes[8].toInt() and 0x3F).toByte()
        uuidBytes[8] = (uuidBytes[8].toInt() or 0x80).toByte()

        val byteBuffer = ByteBuffer.wrap(uuidBytes)
        val mostSignificantBits = byteBuffer.long
        val leastSignificantBits = byteBuffer.long

        return UUID(mostSignificantBits, leastSignificantBits)
    }

    fun generate(
        namespace: Namespace,
        name: String,
    ): String {
        val uuid = generateUUIDv5(namespace, name)
        return uuid.toString().replace("-", "")
    }
}
