package tech.notifly.services

object NotiflyServiceProvider {
    private val serviceRegistration: MutableMap<Class<*>, Any> = mutableMapOf()

    fun <T> register(
        serviceClass: Class<T>,
        instance: T,
    ) {
        serviceRegistration[serviceClass] = instance as Any
    }

    fun <T> unregister(serviceClass: Class<T>) {
        serviceRegistration.remove(serviceClass)
    }

    internal inline fun <reified T> getService(): T = getService(T::class.java)

    fun <T> getService(service: Class<T>): T {
        synchronized(serviceRegistration) {
            return serviceRegistration[service] as T
        }
    }
}
