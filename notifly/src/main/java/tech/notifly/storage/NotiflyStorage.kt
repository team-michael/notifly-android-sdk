package tech.notifly.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.security.crypto.EncryptedSharedPreferences
import tech.notifly.utils.Logger

internal object NotiflyStorage {

    private const val ENCRYPTED_SHARED_PREFERENCES_NAME = "NotiflyAndroidSDK"
    private const val PLAIN_SHARED_PREFERENCES_NAME = "NotiflyAndroidSDKPlainStorage"
    private lateinit var notiflySharedPreferences: SharedPreferences

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return when {
            this::notiflySharedPreferences.isInitialized -> notiflySharedPreferences
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        createEncryptedSharedPreferencesOrThrow(context)
                    } catch (e: Exception) {
                        Logger.e("[Notifly] Failed to create EncryptedSharedPreferences: $e. Falling back to default SharedPreferences.")
                        createSharedPreferences(context)
                    }
                } else {
                    createSharedPreferences(context)
                }
            }
        }
    }

    private fun createSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PLAIN_SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .also {
                notiflySharedPreferences = it
            }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createEncryptedSharedPreferencesOrThrow(context: Context): SharedPreferences {
        return EncryptedSharedPreferences.create(
            ENCRYPTED_SHARED_PREFERENCES_NAME,
            ENCRYPTED_SHARED_PREFERENCES_NAME,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { notiflySharedPreferences = it }
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

    private fun getStringNullable(
        context: Context, key: String, defaultValue: String?
    ): String? {
        return getSharedPreferences(context).getString(key, defaultValue)
    }

    fun <T> put(context: Context, item: NotiflyStorageItem<T>, value: T) {
        with(getSharedPreferences(context)) {
            edit().run {
                when (value) {
                    is Int -> putInt(item.key, value)
                    is Long -> putLong(item.key, value)
                    is Float -> putFloat(item.key, value)
                    is Boolean -> putBoolean(item.key, value)
                    is String -> putString(item.key, value)
                    else -> this /* does nothing */
                }.apply()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(context: Context, item: NotiflyStorageItem<T>): T {
        return when (item.default) {
            is Int -> getInt(context, item.key, item.default as Int) as T
            is Long -> getLong(context, item.key, item.default as Long) as T
            is Float -> getFloat(context, item.key, item.default as Float) as T
            is Boolean -> getBoolean(context, item.key, item.default as Boolean) as T
            is String -> getString(context, item.key, item.default as String) as T
            is String? -> getStringNullable(context, item.key, item.default as String?) as T
            else -> throw IllegalArgumentException("Type Inference Failed for item[${item.key}]")
        }
    }

    fun <T> clear(context: Context, item: NotiflyStorageItem<T>) {
        with(getSharedPreferences(context)) {
            edit().remove(item.key).apply()
            return
        }
    }

    fun clearAll(context: Context) {
        getSharedPreferences(context).edit().clear().apply()
    }
}
