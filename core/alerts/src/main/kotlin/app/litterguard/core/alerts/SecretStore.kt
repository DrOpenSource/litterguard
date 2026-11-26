package app.litterguard.core.alerts

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/** Where the bot token lives. */
internal interface BotCredentials {
    /** The connected bot's @username (not secret), or null. */
    val botUsername: StateFlow<String?>

    fun token(): String?

    fun save(
        token: String,
        botUsername: String,
    )

    fun clear()
}

/** The token encrypted with a Tink AEAD key that is itself protected by the Android Keystore (PRD §7). */
@Singleton
internal class SecretStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BotCredentials {
        private val preferences = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

        private val aead: Aead by lazy {
            AeadConfig.register()
            AndroidKeysetManager
                .Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFERENCES)
                .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }

        private val username = MutableStateFlow(preferences.getString(USERNAME_KEY, null))

        override val botUsername: StateFlow<String?> = username.asStateFlow()

        override fun token(): String? {
            val stored = preferences.getString(TOKEN_KEY, null) ?: return null
            return try {
                String(aead.decrypt(Base64.decode(stored, Base64.NO_WRAP), ASSOCIATED_DATA), Charsets.UTF_8)
            } catch (e: GeneralSecurityException) {
                // For example the Keystore key was lost; the operator has to paste the token again.
                Timber.e("Stored bot token can't be decrypted (%s)", e.javaClass.simpleName)
                null
            }
        }

        override fun save(
            token: String,
            botUsername: String,
        ) {
            val sealed = aead.encrypt(token.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA)
            preferences.edit {
                putString(TOKEN_KEY, Base64.encodeToString(sealed, Base64.NO_WRAP))
                putString(USERNAME_KEY, botUsername)
            }
            username.value = botUsername
        }

        override fun clear() {
            preferences.edit {
                remove(TOKEN_KEY)
                remove(USERNAME_KEY)
            }
            username.value = null
        }

        private companion object {
            const val KEYSET_NAME = "litterguard_keyset"
            const val KEYSET_PREFERENCES = "litterguard_keyset_prefs"
            const val MASTER_KEY_URI = "android-keystore://litterguard_master_key"
            const val TOKEN_KEY = "telegram_token"
            const val USERNAME_KEY = "telegram_bot_username"
            val ASSOCIATED_DATA = "telegram_token".toByteArray(Charsets.UTF_8)
        }
    }
