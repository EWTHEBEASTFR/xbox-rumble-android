package com.zaro.xboxrumble

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaro.xboxrumble.ui.theme.Amber
import com.zaro.xboxrumble.ui.theme.CardBg
import com.zaro.xboxrumble.ui.theme.Coral
import com.zaro.xboxrumble.ui.theme.Green
import com.zaro.xboxrumble.ui.theme.Hairline
import com.zaro.xboxrumble.ui.theme.Ink
import com.zaro.xboxrumble.ui.theme.Lilac
import com.zaro.xboxrumble.ui.theme.MetaText
import com.zaro.xboxrumble.ui.theme.PageBg
import com.zaro.xboxrumble.ui.theme.SecondaryText
import com.zaro.xboxrumble.ui.theme.Teal
import com.zaro.xboxrumble.ui.theme.XboxRumbleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ACTION_USB_PERMISSION = "com.zaro.xboxrumble.USB_PERMISSION"

class MainActivity : ComponentActivity() {
    private lateinit var usbManager: UsbManager
    private var session: XboxController.Session? = null
    private var connectedName by mutableStateOf<String?>(null)
    private var vidPid by mutableStateOf<String?>(null)
    private var statusLine by mutableStateOf(Plug in a controller via USB OTG)
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val dev: UsbDevice? = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
                    if (granted && dev != null) openControllerSession(dev)
                    else statusLine = "USB permission denied"
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val dev: UsbDevice? = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
                    if (dev != null) requestPermissionAndOpen(dev)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val dev: UsbDevice? = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
                    if (dev != null && session?.device?.deviceId == dev.deviceId) {
                        closeSession()
                        statusLine = "Controller disconnected"
                    }
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(usbReceiver, filter)
        }
        val attached: UsbDevice? = intent?.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
        if (attached != null) {
            requestPermissionAndOpen(attached)
        } else {
            val existing = XboxController.findController(usbManager)
            if (existing != null) requestPermissionAndOpen(existing)
        }
        setContent {
            XboxRumbleTheme {
                RumbleScreen(
                    connectedName = connectedName,
                    vidPid = vidPid,
                    statusLine = statusLine,
                    onSendRumble = { heavy -> sendRumbleSafe(heavy) },
                )
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val attached: UsbDevice? = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
        if (attached != null) requestPermissionAndOpen(attached)
    }
    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(usbReceiver) } catch (_: Throwable) {}
        try { session?.sendRumble(0) } catch (_: Throwable) {}
        closeSession()
    }
    private fun requestPermissionAndOpen(device: UsbDevice) {
        if (!XboxController.isXboxController(device)) {
            statusLine = "Connected device isn't a recognized Xbox controller"
            return
        }
        if (usbManager.hasPermission(device)) {
            openControllerSession(device)
            return
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION).setPackage(packageName), flags)
        usbManager.requestPermission(device, pi)
    }
    private fun openControllerSession(device: UsbDevice) {
        closeSession()
        val s = XboxController.openSession(usbManager, device)
        if (s == null) { statusLine = "Failed to open controller"; return }
        session = s
        connectedName = device.productName ?: "Xbox Controller"
        vidPid = String.format("%04X:%04X", device.vendorId, device.productId)
        statusLine = "Controller connected"
        s.sendRumble(0)
    }
    private fun closeSession() {
        try { session?.sendRumble(0) } catch (_: Throwable) {}
        session?.close()
        session = null
        connectedName = null
        vidPid = null
    }
    private fun sendRumbleSafe(heavy: Int) {
        val s = session ?: return
        Thread { try { s.sendRumble(heavy) } catch (_: Throwable) {} }.start()
    }
}