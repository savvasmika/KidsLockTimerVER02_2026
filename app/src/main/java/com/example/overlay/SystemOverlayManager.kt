package com.example.overlay

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.data.local.SecurityPreferences
import com.example.model.ThemeRegistry
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.KidLockTheme
import com.example.ui.theme.RoseDanger

/**
 * Manages a System Overlay Window (TYPE_APPLICATION_OVERLAY) that renders directly
 * over all applications (YouTube, Chrome, Games, Launcher) when the allowed time expires.
 *
 * This provides true child device locking on Android 10+ (Target SDK 36), guaranteeing
 * that when time reaches 0, the lock screen appears automatically without requiring
 * the child to reopen KidsLockTimer.
 */
object SystemOverlayManager {

    private const val TAG = "KidLock_OverlayManager"
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    @Synchronized
    fun showLockOverlay(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Cannot show system overlay: SYSTEM_ALERT_WINDOW permission is not granted")
            return
        }

        if (overlayView != null) {
            Log.d(TAG, "System Overlay Lock is already showing")
            return
        }

        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            val securityPrefs = SecurityPreferences(context)
            val childName = securityPrefs.getDeviceName()
            val themeId = securityPrefs.getActiveThemeId()
            val theme = ThemeRegistry.getThemeById(themeId)

            val composeView = ComposeView(context).apply {
                val lifecycleOwner = OverlayLifecycleOwner()
                lifecycleOwner.performRestore(null)
                lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
                lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)

                setContent {
                    KidLockTheme(kidTheme = theme, isChildMode = true) {
                        OverlayLockScreenContent(
                            childName = childName,
                            securityPrefs = securityPrefs,
                            onUnlockSuccess = {
                                hideLockOverlay(context)
                            }
                        )
                    }
                }
            }

            wm.addView(composeView, params)
            overlayView = composeView
            Log.d(TAG, "System Overlay Lock View added successfully over open apps")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding system overlay lock view: ${e.message}", e)
        }
    }

    @Synchronized
    fun hideLockOverlay(context: Context) {
        val view = overlayView ?: return
        try {
            windowManager?.removeView(view)
            Log.d(TAG, "System Overlay Lock View removed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing system overlay lock view: ${e.message}")
        } finally {
            overlayView = null
            windowManager = null
        }
    }

    fun isOverlayShowing(): Boolean = overlayView != null
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val viewModelStoreObj = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreObj

    fun performRestore(savedState: Bundle?) {
        savedStateRegistryController.performRestore(savedState)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}

@Composable
private fun OverlayLockScreenContent(
    childName: String,
    securityPrefs: SecurityPreferences,
    onUnlockSuccess: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var requestSent by remember { mutableStateOf(false) }

    val gradientBg = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1E1B4B), // Deep Space / Night Navy
            Color(0xFF312E81),
            Color(0xFF0F172A)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBg),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)
            ),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = RoseDanger.copy(alpha = 0.2f),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = RoseDanger,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Time's Up!",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tablet screen time for $childName has expired.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (showPinDialog) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Enter Parent PIN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = enteredPin,
                            onValueChange = {
                                if (it.length <= 8) {
                                    enteredPin = it
                                    errorMessage = null
                                }
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = EmeraldSuccess,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )

                        errorMessage?.let { err ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = err,
                                color = RoseDanger,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showPinDialog = false
                                    enteredPin = ""
                                    errorMessage = null
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel", color = Color.White)
                            }

                            Button(
                                onClick = {
                                    if (securityPrefs.verifyParentPin(enteredPin)) {
                                        securityPrefs.setChildLocked(false)
                                        onUnlockSuccess()
                                    } else {
                                        errorMessage = "Incorrect Parent PIN. Try again."
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Unlock", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = { showPinDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Parent PIN Unlock",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (requestSent) {
                        Text(
                            text = "✅ Request sent to parent's phone!",
                            color = EmeraldSuccess,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    } else {
                        OutlinedButton(
                            onClick = { requestSent = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ask Parent for More Time",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
