package net.miettii.claudetwist.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.miettii.claudetwist.composer.ClaudeAppControl
import net.miettii.claudetwist.composer.DEFAULT_TARGET_PACKAGE_NAME

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// Everything the session and the driver read. [controlDescriptions] starts as the vocabulary
// [ClaudeAppControl] ships and is overridden per control from the Controls screen, so a Claude app
// release that renames one is answered on the phone rather than by a new release of this app.
data class DriveSettings(
    val targetPackageName: String = DEFAULT_TARGET_PACKAGE_NAME,
    val controlDescriptions: Map<ClaudeAppControl, String> = ClaudeAppControl.defaultDescriptions(),
    val shieldTouchesDuringSession: Boolean = true,
    val sessionEnabled: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val targetPackageName = stringPreferencesKey("target_package_name")
        val controlOverrides = stringPreferencesKey("control_overrides")
        val shieldTouches = booleanPreferencesKey("shield_touches")
        val sessionEnabled = booleanPreferencesKey("session_enabled")
    }

    val settings: Flow<DriveSettings> = context.dataStore.data.map { stored ->
        DriveSettings(
            targetPackageName = stored[Keys.targetPackageName]?.takeIf(String::isNotBlank)
                ?: DEFAULT_TARGET_PACKAGE_NAME,
            controlDescriptions = ClaudeAppControl.defaultDescriptions() +
                decodeControlOverrides(stored[Keys.controlOverrides].orEmpty()),
            shieldTouchesDuringSession = stored[Keys.shieldTouches] ?: true,
            sessionEnabled = stored[Keys.sessionEnabled] ?: false,
        )
    }

    suspend fun readSettings(): DriveSettings = settings.first()

    suspend fun setTargetPackageName(packageName: String) = edit { stored ->
        stored[Keys.targetPackageName] = packageName.trim()
    }

    suspend fun setShieldTouchesDuringSession(shield: Boolean) = edit { stored ->
        stored[Keys.shieldTouches] = shield
    }

    suspend fun setSessionEnabled(enabled: Boolean) = edit { stored ->
        stored[Keys.sessionEnabled] = enabled
    }

    /** Teach one control the description the installed Claude app publishes for it. */
    suspend fun setControlDescription(control: ClaudeAppControl, description: String) = edit { stored ->
        val overrides = decodeControlOverrides(stored[Keys.controlOverrides].orEmpty())
        val trimmed = description.trim()
        stored[Keys.controlOverrides] = encodeControlOverrides(
            if (trimmed.isEmpty() || trimmed == control.defaultDescription) {
                overrides - control
            } else {
                overrides + (control to trimmed)
            }
        )
    }

    /** Put the whole vocabulary back to what [ClaudeAppControl] ships. */
    suspend fun forgetControlOverrides() = edit { stored -> stored.remove(Keys.controlOverrides) }

    private suspend fun edit(transform: (MutablePreferences) -> Unit) {
        context.dataStore.edit(transform)
    }

    private companion object {
        // One control per line, name and description split on the first '=' — a description may
        // itself contain one, a control name never does.
        fun encodeControlOverrides(overrides: Map<ClaudeAppControl, String>): String =
            overrides.entries.joinToString("\n") { "${it.key.name}=${it.value}" }

        fun decodeControlOverrides(encoded: String): Map<ClaudeAppControl, String> =
            encoded.lineSequence().mapNotNull { line ->
                val name = line.substringBefore('=', missingDelimiterValue = "")
                val description = line.substringAfter('=', missingDelimiterValue = "")
                val control = ClaudeAppControl.entries.firstOrNull { it.name == name }
                if (control != null && description.isNotEmpty()) control to description else null
            }.toMap()
    }
}
