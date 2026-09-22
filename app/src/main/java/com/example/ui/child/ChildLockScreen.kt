package com.example.ui.child

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.KidTheme
import com.example.network.UpdateCheckState
import com.example.ui.components.AnimatedThemeCanvas
import com.example.ui.components.AppUpdateDialog
import com.example.ui.onboarding.KeypadButton
import com.example.ui.parent.ParentPinDialog
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseDanger

@Composable
fun ChildLockScreen(
    childName: String,
    theme: KidTheme,
    animationsEnabled: Boolean,
    statusMessage: String?,
    updateState: UpdateCheckState = UpdateCheckState.Idle,
    onRequestUnlock: (Int) -> Unit,
    onSubmitUnlockCode: (String, (Boolean) -> Unit) -> Unit,
    onVerifyParentPin: (String) -> Boolean,
    onParentPinUnlockSuccess: () -> Unit,
    onOpenThemeGallery: () -> Unit,
    onOpenPairing: () -> Unit,
    onCheckForUpdates: (String, String) -> Unit = { _, _ -> },
    onDownloadUpdate: (String) -> Unit = {}
) {
    // Intercept and prevent back navigation when tablet is locked
    BackHandler(enabled = true) {
        // Tablet is strictly locked until parent unlocks or enters PIN
    }

    var showCodeDialog by remember { mutableStateOf(false) }
    var showParentPinDialog by remember { mutableStateOf(false) }
    var showRequestTimeDialog by remember { mutableStateOf(false) }
    var requestSentBanner by remember { mutableStateOf(false) }

    // Mascot bouncing animation
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_bounce")
    val bounceScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Theme Animated Canvas Background
        AnimatedThemeCanvas(
            theme = theme,
            animationsEnabled = animationsEnabled
        )

        // Top Status & Parent Unlock Bar (Strict Lock: No settings or themes accessible)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lock Status Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(RoseDanger.copy(alpha = 0.25f))
                    .border(1.dp, RoseDanger.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("badge_tablet_locked")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RoseDanger)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🔒 " + stringResource(R.string.status_locked),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Right Actions: Pairing Button & Discreet Parent PIN Unlock Button
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onOpenPairing,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .testTag("btn_child_pairing_settings")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Pairing Settings",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { showParentPinDialog = true },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .testTag("btn_parent_unlock_pin")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Parent Unlock PIN",
                        tint = Color.White
                    )
                }
            }
        }

        // Center Lock Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Mascot with Locked Border
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(if (animationsEnabled) bounceScale else 1f)
                    .clip(CircleShape)
                    .background(theme.primaryColor.copy(alpha = 0.3f))
                    .border(3.dp, RoseDanger, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = theme.mascotEmoji,
                    fontSize = 68.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Message Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = theme.surfaceColor.copy(alpha = 0.88f)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.break_time_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.break_time_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )

                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = theme.accentColor
                        )
                    }

                    if (requestSentBanner) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "⭐ " + stringResource(R.string.request_sent_notice),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Child Action Buttons (Request time or enter parent-provided temporary code)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Request time from parent button
                Button(
                    onClick = { showRequestTimeDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("btn_request_more_time"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor)
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.btn_request_time),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Enter 6-digit temporary unlock code button
                Button(
                    onClick = { showCodeDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("btn_enter_temp_code"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceColor)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = theme.accentColor)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.btn_enter_code),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    // Enter Temporary Unlock Code Dialog
    if (showCodeDialog) {
        EnterCodeModal(
            onSubmitCode = { code, onResult ->
                onSubmitUnlockCode(code) { success ->
                    onResult(success)
                    if (success) {
                        showCodeDialog = false
                    }
                }
            },
            onDismiss = { showCodeDialog = false }
        )
    }

    // Parent PIN Verification Dialog
    if (showParentPinDialog) {
        ParentPinDialog(
            onVerifyPin = onVerifyParentPin,
            onSuccess = {
                showParentPinDialog = false
                onParentPinUnlockSuccess()
            },
            onDismiss = { showParentPinDialog = false }
        )
    }

    // Request Time Dialog (Choice from 5m up to 2 hours)
    if (showRequestTimeDialog) {
        RequestTimeModal(
            theme = theme,
            onRequest = { selectedMins ->
                onRequestUnlock(selectedMins)
                requestSentBanner = true
                showRequestTimeDialog = false
            },
            onDismiss = { showRequestTimeDialog = false }
        )
    }
}

@Composable
private fun RequestTimeModal(
    theme: KidTheme,
    onRequest: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMinutes by remember { mutableStateOf(30) }
    val options = listOf(
        5 to R.string.time_5m,
        10 to R.string.time_10m,
        15 to R.string.time_15m,
        20 to R.string.time_20m,
        30 to R.string.time_30m,
        45 to R.string.time_45m,
        60 to R.string.time_60m,
        90 to R.string.time_90m,
        120 to R.string.time_120m
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.ask_time_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.select_time_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    items(options) { (mins, stringResId) ->
                        val isSelected = selectedMinutes == mins
                        Surface(
                            onClick = { selectedMinutes = mins },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) theme.primaryColor else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("chip_req_time_$mins")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(stringResId),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onRequest(selectedMinutes) },
                modifier = Modifier.testTag("btn_confirm_request_time")
            ) {
                Text(stringResource(R.string.btn_request_time))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_request_time")
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun EnterCodeModal(
    onSubmitCode: (String, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var enteredCode by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.enter_code_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.enter_code_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 6-digit Code Box
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = enteredCode.padEnd(6, '-'),
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                            .testTag("text_input_code_display"),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 6.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (isError) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.invalid_code_error),
                        color = RoseDanger,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 6-digit Keypad
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "DEL")
                )

                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Spacer(modifier = Modifier.size(52.dp))
                            } else {
                                Box(modifier = Modifier.size(52.dp)) {
                                    KeypadButton(
                                        text = key,
                                        isDelete = key == "DEL",
                                        onClick = {
                                            isError = false
                                            if (key == "DEL") {
                                                if (enteredCode.isNotEmpty()) {
                                                    enteredCode = enteredCode.dropLast(1)
                                                }
                                            } else {
                                                if (enteredCode.length < 6) {
                                                    enteredCode += key
                                                    if (enteredCode.length == 6) {
                                                        onSubmitCode(enteredCode) { success ->
                                                            if (!success) {
                                                                isError = true
                                                                enteredCode = ""
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_close_enter_code")
            ) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
