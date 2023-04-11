package tech.notifly

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences

object NotiflySharedPreferences {

    private const val PREFERENCES_NAME = "Notifly"
    private lateinit var notiflySharedPreferences: SharedPreferences

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return when {
            this::notiflySharedPreferences.isInitialized -> notiflySharedPreferences
            else -> EncryptedSharedPreferences.create(
                PREFERENCES_NAME,
                PREFERENCES_NAME,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { notiflySharedPreferences = it }
        }
    }

    private fun getInt(context: Context, key: String, defaultValue: Int): Int {
        return getSharedPreferences(context).getInt(key, defaultValue)
    }

    private fun getLong(context: Context, key: String, defaultValue: Long): Long {
        return getSharedPreferences(context).getLong(key, defaultValue)
    }

    private fun getFloat(context: Context, key: String, defaultValue: Float): Float {
        return getSharedPreferences(context).getFloat(key, defaultValue)
    }

    private fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
        return getSharedPreferences(context).getBoolean(key, defaultValue)
    }

    private fun getString(context: Context, key: String, defaultValue: String): String {
        return getSharedPreferences(context).getString(key, defaultValue) ?: defaultValue
    }

    private fun getStringNullable(context: Context, key: String, defaultValue: String?): String? {
        return getSharedPreferences(context).getString(key, defaultValue)
    }

    fun <T> put(context: Context, key: String, value: T) {
        with(getSharedPreferences(context)) {
            if (value == null) {
                edit().remove(key).apply()
                return
            }

            edit().run {
                when (value) {
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    else -> this /* does nothing */
                }.apply()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(context: Context, key: String, default: Any?): T {
        return when (default) {
            is Int -> getInt(context, key, default) as T
            is Long -> getLong(context, key, default) as T
            is Float -> getFloat(context, key, default) as T
            is Boolean -> getBoolean(context, key, default) as T
            is String -> getString(context, key, default) as T
            is String? -> getStringNullable(context, key, default) as T
            else -> throw IllegalArgumentException("Invalid Type for 'default' parameter: ${default::javaClass}")
        }
    }

    fun clear(context: Context) {
        getSharedPreferences(context).edit().clear().apply()
    }
}
