package com.wavv.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reports the codec Android negotiated for the active A2DP connection. */
internal class BluetoothCodecTracker(private val context: Context) {
    private val _codec = MutableStateFlow<String?>(null)
    val codec = _codec.asStateFlow()
    private var started = false
    private var profile: BluetoothProfile? = null

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profileType: Int, proxy: BluetoothProfile) {
            if (profileType == BluetoothProfile.A2DP) {
                profile = proxy
                refreshFromProfile(proxy)
            }
        }

        override fun onServiceDisconnected(profileType: Int) {
            if (profileType == BluetoothProfile.A2DP) {
                profile = null
                _codec.value = null
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CODEC_CONFIG_CHANGED) {
                val status = parcelableExtra(intent, CODEC_STATUS_EXTRA)
                val codecName = status?.let(::codecName)
                if (status != null) _codec.value = codecName
                else profile?.let(::refreshFromProfile)
            } else if (intent?.action == A2DP_CONNECTION_CHANGED) {
                profile?.let(::refreshFromProfile)
            }
        }
    }

    fun start() {
        if (started || !hasConnectPermission()) return
        started = true
        val filter = IntentFilter().apply {
            addAction(CODEC_CONFIG_CHANGED)
            addAction(A2DP_CONNECTION_CHANGED)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
        }
        runCatching { BluetoothAdapter.getDefaultAdapter()?.getProfileProxy(context, listener, BluetoothProfile.A2DP) }
    }

    fun stop() {
        if (!started) return
        runCatching { context.unregisterReceiver(receiver) }
        profile?.let { runCatching { BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.A2DP, it) } }
        profile = null
        started = false
        _codec.value = null
    }

    private fun refreshFromProfile(proxy: BluetoothProfile) {
        if (!hasConnectPermission()) return
        runCatching {
            val device = proxy.connectedDevices.firstOrNull() ?: return
            // Android does not expose the active A2DP codec through a public getter.
            // Read the same negotiated status broadcast payload used by Android's A2DP service.
            val status = proxy.javaClass.methods.firstOrNull {
                it.name == "getCodecStatus" && it.parameterTypes.size == 1
            }?.invoke(proxy, device)
            val codecName = status?.let(::codecName)
            _codec.value = codecName
        }
    }

    private fun codecName(status: Any): String? {
        val config = runCatching {
            status.javaClass.methods.firstOrNull {
                it.name == "getCodecConfig" && it.parameterTypes.isEmpty()
            }?.invoke(status)
        }.getOrNull() ?: return null
        val extendedName = runCatching {
            config.javaClass.methods.firstOrNull { it.name == "getExtendedCodecType" }
                ?.invoke(config)?.let { ext ->
                    ext.javaClass.methods.firstOrNull { it.name == "getCodecName" }?.invoke(ext) as? String
                }
        }.getOrNull()?.trim()?.takeIf(String::isNotEmpty)
        val reportedName = extendedName ?: runCatching {
            config.javaClass.methods.firstOrNull {
                it.name == "getCodecName" && it.parameterTypes.isEmpty()
            }?.invoke(config) as? String
        }.getOrNull()?.trim()?.takeIf(String::isNotEmpty)
        if (reportedName != null) return reportedName

        // BluetoothCodecConfig exposes the codec type publicly on Android 13+.
        // getCodecName() is hidden on many platform builds, so map standard types directly.
        val codecType = runCatching {
            (config.javaClass.methods.firstOrNull {
                it.name == "getCodecType" && it.parameterTypes.isEmpty()
            }?.invoke(config) as? Number)?.toInt()
        }.getOrNull() ?: return null
        return when (codecType) {
            0 -> "SBC"
            1 -> "AAC"
            2 -> "aptX"
            3 -> "aptX HD"
            4 -> "LDAC"
            5 -> "LC3"
            6 -> "Opus"
            else -> "Bluetooth codec"
        }
    }

    private fun parcelableExtra(intent: Intent, key: String): Any? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(key, android.os.Parcelable::class.java)
        else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<android.os.Parcelable>(key)
        }
    }.getOrNull()

    private fun hasConnectPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CODEC_CONFIG_CHANGED = "android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED"
        const val CODEC_STATUS_EXTRA = "android.bluetooth.extra.CODEC_STATUS"
        const val A2DP_CONNECTION_CHANGED = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
    }
}
