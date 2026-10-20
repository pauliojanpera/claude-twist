package net.miettii.claudetwist

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.miettii.claudetwist.composer.ClaudeAppControl
import net.miettii.claudetwist.composer.ClaudeAppDriverService
import net.miettii.claudetwist.data.DriveSettings
import net.miettii.claudetwist.data.SettingsRepository
import net.miettii.claudetwist.gesture.TwistSensitivity
import net.miettii.claudetwist.gesture.TwistSensor
import net.miettii.claudetwist.session.ScreenAwakeOverlay
import net.miettii.claudetwist.session.WalkSessionService
import net.miettii.claudetwist.ui.ClaudeTwistTheme

class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* reported in the UI */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val settings = SettingsRepository(applicationContext)
        setContent {
            ClaudeTwistTheme {
                Surface(modifier = Modifier.fillMaxSize()) { SessionScreen(settings) }
            }
        }
    }
}

/**
 * Whether the holder has enabled this app's driver in the system's accessibility settings. Read from
 * the secure setting rather than from the service itself, so it is right the instant this screen
 * opens — and so a sideloaded build that the system is still holding behind restricted settings
 * reports honestly.
 */
private fun isDriverEnabled(context: Context): Boolean {
    val component = ComponentName(context, ClaudeAppDriverService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return enabled.split(':').any {
        it.equals(component.flattenToString(), ignoreCase = true) ||
            it.equals(component.flattenToShortString(), ignoreCase = true)
    }
}

private fun isExemptFromBatteryOptimization(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

/**
 * Ask to be left out of battery optimisation, which keeps a long session from being cut short. This
 * app is sideloaded and never distributed through Play, so the store policy on this action does not
 * reach it.
 */
@Suppress("BatteryLife")
private fun askForBatteryOptimizationExemption(context: Context) =
    openSettings(context, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, perPackage = true)

private fun openSettings(context: Context, action: String, perPackage: Boolean = false) {
    val intent = Intent(action)
        .apply { if (perPackage) data = Uri.fromParts("package", context.packageName, null) }
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun SessionScreen(settings: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val stored by settings.settings.collectAsState(initial = DriveSettings())

    // The readiness of things the holder grants outside this app: re-read whenever the screen comes
    // back, which is when a round trip through the system settings ends.
    var readiness by remember { mutableStateOf(readReadiness(context)) }
    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) readiness = readReadiness(context)
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        readiness = readReadiness(context)
    }
    val phoneStateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        readiness = readReadiness(context)
    }

    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(
            "${BuildConfig.APPLICATION_ID} ${BuildConfig.BUILD_DATE}-${BuildConfig.BUILD_COMMIT}",
            style = MaterialTheme.typography.bodySmall,
        )

        ReadinessSection(readiness, context)

        HorizontalDivider()

        Text(stringResource(R.string.session_heading), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.session_explanation), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = readiness.isReadyToDrive,
                onClick = {
                    scope.launch { settings.setSessionEnabled(true) }
                    WalkSessionService.start(context)
                },
            ) { Text(stringResource(R.string.session_start)) }
            OutlinedButton(onClick = {
                scope.launch { settings.setSessionEnabled(false) }
                WalkSessionService.stop(context)
            }) { Text(stringResource(R.string.session_stop)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = stored.shieldTouchesDuringSession,
                onCheckedChange = { scope.launch { settings.setShieldTouchesDuringSession(it) } },
            )
            Text(
                stringResource(R.string.shield_touches_label),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Text(stringResource(R.string.sensitivity_heading), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.sensitivity_explanation), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TwistSensitivity.entries.forEach { sensitivity ->
                FilterChip(
                    selected = stored.twistSensitivity == sensitivity,
                    onClick = { scope.launch { settings.setTwistSensitivity(sensitivity) } },
                    label = { Text(stringResource(sensitivity.labelResource)) },
                )
            }
        }

        HorizontalDivider()

        Text(stringResource(R.string.trigger_heading), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.trigger_explanation), style = MaterialTheme.typography.bodySmall)
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.trigger_hazard),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp),
            )
        }
        if (readiness.isCallRedirectionAvailable) {
            RequirementRow(
                met = readiness.isCallRedirectionRoleHeld,
                label = stringResource(R.string.readiness_call_redirection),
                detail = stringResource(R.string.readiness_call_redirection_detail),
                actionLabel = stringResource(R.string.readiness_open_call_redirection),
                onAction = {
                    callRedirectionRole(context)
                        ?.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION)
                        ?.let(roleLauncher::launch)
                },
            )
            RequirementRow(
                met = readiness.hasPhoneStateAccess,
                label = stringResource(R.string.readiness_phone_state),
                detail = stringResource(R.string.readiness_phone_state_detail),
                actionLabel = stringResource(R.string.readiness_grant_phone_state),
                onAction = { phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = stored.divertDialAttemptsDuringSession,
                    onCheckedChange = { scope.launch { settings.setDivertDialAttemptsDuringSession(it) } },
                )
                Text(
                    stringResource(R.string.trigger_divert_label),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        HorizontalDivider()

        ControlsSection(stored, settings, scope)
    }
}

private val TwistSensitivity.labelResource: Int
    get() = when (this) {
        TwistSensitivity.OneTwist -> R.string.sensitivity_one_twist
        TwistSensitivity.OneAndAHalfTwists -> R.string.sensitivity_one_and_a_half_twists
        TwistSensitivity.TwoTwists -> R.string.sensitivity_two_twists
    }

private data class Readiness(
    val hasGyroscope: Boolean,
    val isDriverEnabled: Boolean,
    val canDrawOverlay: Boolean,
    val isExemptFromBatteryOptimization: Boolean,
    val isCallRedirectionAvailable: Boolean,
    val isCallRedirectionRoleHeld: Boolean,
    val hasPhoneStateAccess: Boolean,
) {
    // The overlay and the battery exemption only make a session survive longer; without the driver
    // or a gyroscope there is nothing to start.
    val isReadyToDrive: Boolean get() = hasGyroscope && isDriverEnabled
}

private fun readReadiness(context: Context) = Readiness(
    hasGyroscope = TwistSensor.hasGyroscope(context),
    isDriverEnabled = isDriverEnabled(context),
    canDrawOverlay = ScreenAwakeOverlay(context).isPermitted,
    isExemptFromBatteryOptimization = isExemptFromBatteryOptimization(context),
    isCallRedirectionAvailable = callRedirectionRole(context) != null,
    isCallRedirectionRoleHeld = callRedirectionRole(context)
        ?.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION) == true,
    hasPhoneStateAccess = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
        PackageManager.PERMISSION_GRANTED,
)

/**
 * The role manager, but only where the call redirection role exists to be asked for — a device may
 * not carry the role even on a platform that defines it, and then the headset button is not on offer.
 */
private fun callRedirectionRole(context: Context): RoleManager? =
    context.getSystemService(RoleManager::class.java)
        ?.takeIf { it.isRoleAvailable(RoleManager.ROLE_CALL_REDIRECTION) }

@Composable
private fun ReadinessSection(readiness: Readiness, context: Context) {
    Text(stringResource(R.string.readiness_heading), style = MaterialTheme.typography.titleSmall)
    if (!readiness.hasGyroscope) {
        Text(stringResource(R.string.readiness_no_gyroscope), color = MaterialTheme.colorScheme.error)
    }
    RequirementRow(
        met = readiness.isDriverEnabled,
        label = stringResource(R.string.readiness_driver),
        detail = stringResource(R.string.readiness_driver_detail),
        actionLabel = stringResource(R.string.readiness_open_accessibility),
        onAction = { openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) },
    )
    RequirementRow(
        met = readiness.canDrawOverlay,
        label = stringResource(R.string.readiness_overlay),
        detail = stringResource(R.string.readiness_overlay_detail),
        actionLabel = stringResource(R.string.readiness_open_overlay),
        onAction = { openSettings(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, perPackage = true) },
    )
    RequirementRow(
        met = readiness.isExemptFromBatteryOptimization,
        label = stringResource(R.string.readiness_battery),
        detail = stringResource(R.string.readiness_battery_detail),
        actionLabel = stringResource(R.string.readiness_open_battery),
        onAction = { askForBatteryOptimizationExemption(context) },
    )
    if (!readiness.isDriverEnabled) {
        Text(stringResource(R.string.readiness_restricted_settings), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RequirementRow(
    met: Boolean,
    label: String,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        color = if (met) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${if (met) "✓" else "✗"}  $label", style = MaterialTheme.typography.titleSmall)
            if (!met) {
                Text(detail, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/**
 * The control vocabulary, and what the window in front publishes right now. This is how a Claude app
 * release that renames a control is answered: put the Claude app in the state that shows the control,
 * come back, and the list below names it.
 */
@Composable
private fun ControlsSection(
    stored: DriveSettings,
    settings: SettingsRepository,
    scope: CoroutineScope,
) {
    var descriptionsInFront by remember { mutableStateOf(emptyList<String>()) }
    Text(stringResource(R.string.controls_heading), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.controls_explanation), style = MaterialTheme.typography.bodySmall)

    ClaudeAppControl.entries.forEach { control ->
        val description = stored.controlDescriptions[control].orEmpty()
        OutlinedTextField(
            value = description,
            onValueChange = { scope.launch { settings.setControlDescription(control, it) } },
            label = { Text(control.name) },
            supportingText = {
                if (description != control.defaultDescription) {
                    Text(stringResource(R.string.controls_default, control.defaultDescription))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    OutlinedTextField(
        value = stored.targetPackageName,
        onValueChange = { scope.launch { settings.setTargetPackageName(it) } },
        label = { Text(stringResource(R.string.controls_target_package)) },
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            descriptionsInFront = ClaudeAppDriverService.connectedInstance
                ?.collectDescriptionsInFront()
                .orEmpty()
        }) { Text(stringResource(R.string.controls_read_screen)) }
        OutlinedButton(onClick = { scope.launch { settings.forgetControlOverrides() } }) {
            Text(stringResource(R.string.controls_reset))
        }
    }

    if (descriptionsInFront.isNotEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                descriptionsInFront.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
