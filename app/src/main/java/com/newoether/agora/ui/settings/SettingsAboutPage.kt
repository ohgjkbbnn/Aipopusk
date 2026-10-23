package com.newoether.agora.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.newoether.agora.ui.motion.MotionAwareCircularProgressIndicator as CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.common.LocalAgoraHaptics
import com.newoether.agora.viewmodel.ChatViewModel
import com.newoether.agora.util.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAboutPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val packageInfo = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (_: Exception) { null }
    }
    val versionName = packageInfo?.versionName ?: "?"
    @Suppress("DEPRECATION")
    val versionCode = packageInfo?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode
        else it.versionCode.toLong()
    } ?: 0L
    val developerOptionsEnabled by viewModel.settings.developerOptionsEnabled.collectAsState()
    var developerTapCount by rememberSaveable { mutableIntStateOf(0) }
    val haptics = LocalAgoraHaptics.current
    val developerTapsRemainingFormat = stringResource(R.string.developer_options_taps_remaining)
    val developerEnabledMessage = stringResource(R.string.developer_options_enabled_message)
    val developerAlreadyEnabledMessage = stringResource(R.string.developer_options_already_enabled_message)

    var showLicenseDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val focusManager = LocalFocusManager.current

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun onVersionTapped() {
        val result = DeveloperModeUnlockPolicy.advance(
            currentTapCount = developerTapCount,
            alreadyEnabled = developerOptionsEnabled,
        )
        developerTapCount = result.tapCount
        when (result.feedback) {
            DeveloperUnlockFeedback.NONE -> haptics.selection()
            DeveloperUnlockFeedback.REMAINING_TAPS -> {
                haptics.selection()
                viewModel.emitSnackbar(
                    String.format(developerTapsRemainingFormat, result.remainingTaps),
                )
            }
            DeveloperUnlockFeedback.ENABLED -> {
                haptics.confirm()
                viewModel.settings.setDeveloperOptionsEnabled(true)
                viewModel.emitSnackbar(developerEnabledMessage)
            }
            DeveloperUnlockFeedback.ALREADY_ENABLED -> {
                haptics.selection()
                viewModel.emitSnackbar(developerAlreadyEnabledMessage)
            }
        }
    }

    if (showLicenseDialog) {
        LicenseDialog(onDismiss = { showLicenseDialog = false })
    }

    CollapsingSettingsScaffold(
        title = stringResource(R.string.about_title),
        onBack = onBack
    ) {
            SettingsGroupColumn {
                // -- App Info --
                SettingsGroup(title = stringResource(R.string.about_info), items = listOf({
                SettingsItem(
                    headlineContent = { Text(stringResource(R.string.about_developer)) },
                    supportingContent = { Text(stringResource(R.string.about_developer_name)) },
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
                )
            }, {
                SettingsItem(
                    headlineContent = { Text(stringResource(R.string.about_version)) },
                    supportingContent = { Text("v$versionName ($versionCode)") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = ::onVersionTapped),
                )
            }))

            // -- Links --
            SettingsGroup(title = stringResource(R.string.about_links), items = listOf({
                SettingsItem(
                    headlineContent = { Text(stringResource(R.string.about_license)) },
                    supportingContent = { Text(stringResource(R.string.about_license_desc)) },
                    leadingContent = { Icon(Icons.Default.Policy, contentDescription = null) },
                    modifier = Modifier.clickable { showLicenseDialog = true }
                )
            }))

            // -- Origin / attribution (required: this app is based on Agora, GPL-3.0) --
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                    OriginCredit()
                }
            }
            }
    }
}

@Composable
private fun OriginCredit() {
    val context = LocalContext.current
    val modifiedPackage = remember(context.packageName) { ratingUsesDifferentPackage(context.packageName) }
    val projectUrl = remember {
        decodeRatingOriginText("aHR0cHM6Ly9naXRodWIuY29tL25ld28tZXRoZXIvQWdvcmE=")
    }
    Column(Modifier.fillMaxWidth()) {
        if (modifiedPackage) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = decodeRatingOriginText(stringResource(R.string.rating_origin_modified_title_b64)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = decodeRatingOriginText(stringResource(R.string.rating_origin_modified_body_b64)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            text = decodeRatingOriginText(stringResource(R.string.rating_origin_credit_b64)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(projectUrl))) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = decodeRatingOriginText(stringResource(
                    if (modifiedPackage) R.string.rating_origin_view_project_b64
                    else R.string.rating_origin_project_b64
                )),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
