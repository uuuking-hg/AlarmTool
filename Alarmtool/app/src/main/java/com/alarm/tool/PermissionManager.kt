package com.alarm.tool

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.widget.Toast
import android.util.Log
import android.content.SharedPreferences
import android.os.PowerManager

enum class PermissionType {
    CAMERA,
    EXACT_ALARM,
    OVERLAY,
    AUTOSTART,
    BATTERY_OPTIMIZATION,
    VIBRATE,
    INTERNET,
    RECORD_AUDIO
}

class PermissionManager(private val activity: ComponentActivity) {

    private var requestPermissionLauncher: ActivityResultLauncher<String>
    private var requestExactAlarmPermissionLauncher: ActivityResultLauncher<Intent>? = null
    private val prefs: SharedPreferences by lazy {
        activity.getSharedPreferences("alarmtool_prefs", Context.MODE_PRIVATE)
    }
    private val TAG = "PermissionManager"

    companion object {
        private const val KEY_AUTOSTART_GUIDE_SHOWN = "hasShownAutostartGuide"
        private const val KEY_BATTERY_OPT_GUIDE_SHOWN = "hasShownBatteryOptGuide"
        private const val KEY_OVERLAY_GUIDE_SHOWN = "hasShownOverlayGuide"
        private const val KEY_EXACT_ALARM_GUIDE_SHOWN = "hasShownExactAlarmGuide"
        private const val KEY_LAST_PERMISSION_CHECK = "lastPermissionCheck"
        private const val PERMISSION_CHECK_INTERVAL = 24 * 60 * 60 * 1000 // 24小时
    }

    init {
        requestPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            Log.d(TAG, "Permission request result: $isGranted")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestExactAlarmPermissionLauncher = activity.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                Log.d(TAG, "Exact alarm permission request completed")
            }
        }
    }

    fun hasAllRequiredPermissions(): Boolean {
        // 检查是否需要重新检查权限
        val lastCheck = prefs.getLong(KEY_LAST_PERMISSION_CHECK, 0)
        val now = System.currentTimeMillis()
        if (now - lastCheck < PERMISSION_CHECK_INTERVAL) {
            return true
        }

        val hasAll = hasExactAlarmPermission() &&
                     hasOverlayPermission() &&
                     hasCameraPermission() &&
                     hasVibratePermission() &&
                     hasInternetPermission() &&
                     hasRecordAudioPermission()

        if (hasAll) {
            // 更新最后检查时间
            prefs.edit().putLong(KEY_LAST_PERMISSION_CHECK, now).apply()
        }
        return hasAll
    }

    fun requestPermissionsIfNeeded(
        permissionTypes: Set<PermissionType>,
        onAllGranted: () -> Unit = {},
        onSomeGranted: (Set<PermissionType>) -> Unit = {},
        onAllDenied: () -> Unit = {}
    ) {
        // 检查是否需要重新请求权限
        val lastCheck = prefs.getLong(KEY_LAST_PERMISSION_CHECK, 0)
        val now = System.currentTimeMillis()
        if (now - lastCheck < PERMISSION_CHECK_INTERVAL) {
            onAllGranted()
            return
        }

        val grantedPermissions = mutableSetOf<PermissionType>()
        val deniedPermissions = mutableSetOf<PermissionType>()

        permissionTypes.forEach { type ->
            when (type) {
                PermissionType.CAMERA -> {
                    if (hasCameraPermission()) grantedPermissions.add(type)
                    else deniedPermissions.add(type)
                }
                PermissionType.EXACT_ALARM -> {
                    if (hasExactAlarmPermission() || prefs.getBoolean(KEY_EXACT_ALARM_GUIDE_SHOWN, false)) {
                        grantedPermissions.add(type)
                    } else {
                        deniedPermissions.add(type)
                    }
                }
                PermissionType.OVERLAY -> {
                    if (hasOverlayPermission() || prefs.getBoolean(KEY_OVERLAY_GUIDE_SHOWN, false)) {
                        grantedPermissions.add(type)
                    } else {
                        deniedPermissions.add(type)
                    }
                }
                PermissionType.AUTOSTART -> {
                    if (prefs.getBoolean(KEY_AUTOSTART_GUIDE_SHOWN, false)) {
                        grantedPermissions.add(type)
                    } else {
                        deniedPermissions.add(type)
                    }
                }
                PermissionType.BATTERY_OPTIMIZATION -> {
                    if (isIgnoringBatteryOptimizations() || prefs.getBoolean(KEY_BATTERY_OPT_GUIDE_SHOWN, false)) {
                        grantedPermissions.add(type)
                    } else {
                        deniedPermissions.add(type)
                    }
                }
                PermissionType.RECORD_AUDIO -> {
                    if (hasRecordAudioPermission()) grantedPermissions.add(type)
                    else deniedPermissions.add(type)
                }
                PermissionType.VIBRATE, PermissionType.INTERNET -> {
                    grantedPermissions.add(type)
                }
            }
        }

        when {
            deniedPermissions.isEmpty() -> {
                prefs.edit().putLong(KEY_LAST_PERMISSION_CHECK, now).apply()
                onAllGranted()
            }
            grantedPermissions.isNotEmpty() -> onSomeGranted(grantedPermissions)
            else -> onAllDenied()
        }

        // 请求被拒绝的权限
        deniedPermissions.forEach { requestPermission(it) }
    }

    private fun requestPermission(type: PermissionType) {
        when (type) {
            PermissionType.CAMERA -> requestCameraPermission(
                onGranted = { Log.d(TAG, "Camera permission granted") },
                onDenied = { Log.d(TAG, "Camera permission denied") }
            )
            PermissionType.EXACT_ALARM -> requestExactAlarmPermission(
                onGranted = { Log.d(TAG, "Exact alarm permission granted") },
                onDenied = { Log.d(TAG, "Exact alarm permission denied") }
            )
            PermissionType.OVERLAY -> requestOverlayPermission()
            PermissionType.AUTOSTART -> requestAutoStartPermission()
            PermissionType.BATTERY_OPTIMIZATION -> requestBatteryOptimizationPermission()
            PermissionType.RECORD_AUDIO -> requestRecordAudioPermission(
                onGranted = { Log.d(TAG, "Record audio permission granted") },
                onDenied = { Log.d(TAG, "Record audio permission denied") }
            )
            else -> { /* 其他权限在安装时已授予 */ }
        }
    }

    fun requestCameraPermission(onGranted: () -> Unit, onDenied: () -> Unit) {
        when {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                onGranted()
            }
            activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationaleDialog(
                    title = "相机权限",
                    message = activity.getString(R.string.permission_rationale_camera),
                    onConfirm = { requestPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onDeny = onDenied
                )
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    fun requestExactAlarmPermission(onGranted: () -> Unit, onDenied: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms() || prefs.getBoolean(KEY_EXACT_ALARM_GUIDE_SHOWN, false)) {
                onGranted()
            } else {
                showPermissionRationaleDialog(
                    title = "精确闹钟权限",
                    message = activity.getString(R.string.permission_rationale_exact_alarm),
                    onConfirm = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${activity.packageName}")
                            }
                            requestExactAlarmPermissionLauncher?.launch(intent)
                            prefs.edit().putBoolean(KEY_EXACT_ALARM_GUIDE_SHOWN, true).apply()
                            onGranted()
                        } catch (e: Exception) {
                            onDenied()
                        }
                    },
                    onDeny = {
                        prefs.edit().putBoolean(KEY_EXACT_ALARM_GUIDE_SHOWN, true).apply()
                        onDenied()
                    }
                )
            }
        } else {
            onGranted()
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity) &&
            !prefs.getBoolean(KEY_OVERLAY_GUIDE_SHOWN, false)) {
            showPermissionRationaleDialog(
                title = "悬浮窗权限",
                message = "为保证闹钟任务界面能正常弹出，请开启\"悬浮窗/后台弹窗\"权限。",
                onConfirm = {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${activity.packageName}")
                        )
                        activity.startActivity(intent)
                        Toast.makeText(activity, "请在列表中查找并开启\"悬浮窗\"、\"后台弹窗\"或\"显示在其他应用上层\"权限", Toast.LENGTH_LONG).show()
                        prefs.edit().putBoolean(KEY_OVERLAY_GUIDE_SHOWN, true).apply()
                    } catch (e: ActivityNotFoundException) {
                        openAppSettings()
                        Toast.makeText(activity, "请在本应用设置中查找\"特殊权限\"或\"显示在其他应用上层\"并开启", Toast.LENGTH_LONG).show()
                        prefs.edit().putBoolean(KEY_OVERLAY_GUIDE_SHOWN, true).apply()
                    }
                },
                onDeny = {
                    prefs.edit().putBoolean(KEY_OVERLAY_GUIDE_SHOWN, true).apply()
                }
            )
        }
    }

    private fun requestAutoStartPermission() {
        if (prefs.getBoolean(KEY_AUTOSTART_GUIDE_SHOWN, false)) return

        showPermissionRationaleDialog(
            title = "自启动权限",
            message = "为保证闹钟能准时弹出，请在系统设置中开启\"自启动\"权限。",
            onConfirm = {
                try {
                    val intent = Intent().apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        when (Build.MANUFACTURER.lowercase()) {
                            "xiaomi" -> {
                                component = android.content.ComponentName(
                                    "com.miui.securitycenter",
                                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                                )
                            }
                            "huawei" -> {
                                component = android.content.ComponentName(
                                    "com.huawei.systemmanager",
                                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                                )
                            }
                            "oppo" -> {
                                component = android.content.ComponentName(
                                    "com.coloros.safecenter",
                                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                                )
                            }
                            "vivo" -> {
                                component = android.content.ComponentName(
                                    "com.iqoo.secure",
                                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                                )
                            }
                            else -> {
                                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                data = Uri.parse("package:${activity.packageName}")
                            }
                        }
                    }
                    activity.startActivity(intent)
                    Toast.makeText(activity, "请在列表中查找并开启\"自启动\"、\"后台运行\"或\"应用启动管理\"相关权限", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    openAppSettings()
                    Toast.makeText(activity, "请在本应用设置中查找\"自启动\"、\"后台运行\"或\"应用启动管理\"相关权限并开启", Toast.LENGTH_LONG).show()
                }
                prefs.edit().putBoolean(KEY_AUTOSTART_GUIDE_SHOWN, true).apply()
            },
            onDeny = {
                prefs.edit().putBoolean(KEY_AUTOSTART_GUIDE_SHOWN, true).apply()
            }
        )
    }

    private fun requestBatteryOptimizationPermission() {
        if (prefs.getBoolean(KEY_BATTERY_OPT_GUIDE_SHOWN, false)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations() &&
            !prefs.getBoolean(KEY_BATTERY_OPT_GUIDE_SHOWN, false)) {
            showPermissionRationaleDialog(
                title = "电池优化",
                message = "为确保闹钟正常运行，请关闭电池优化。",
                onConfirm = {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${activity.packageName}")
                        }
                        activity.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        openAppSettings()
                        Toast.makeText(activity, "请在电池设置中关闭本应用的电池优化", Toast.LENGTH_LONG).show()
                    }
                    prefs.edit().putBoolean(KEY_BATTERY_OPT_GUIDE_SHOWN, true).apply()
                },
                onDeny = {
                    prefs.edit().putBoolean(KEY_BATTERY_OPT_GUIDE_SHOWN, true).apply()
                }
            )
        }
    }

    private fun requestRecordAudioPermission(onGranted: () -> Unit, onDenied: () -> Unit) {
        when {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                onGranted()
            }
            activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                showPermissionRationaleDialog(
                    title = "录音权限",
                    message = activity.getString(R.string.permission_rationale_record_audio),
                    onConfirm = { requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onDeny = onDenied
                )
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun showPermissionRationaleDialog(
        title: String,
        message: String,
        onConfirm: () -> Unit,
        onDeny: () -> Unit = {}
    ) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ -> onConfirm() }
            .setNegativeButton("取消") { _, _ -> onDeny() }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        activity.startActivity(intent)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun hasExactAlarmPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        } else true

    private fun hasOverlayPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else true

    private fun hasVibratePermission(): Boolean = true // 安装时授予

    fun hasInternetPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(activity.packageName)
        }
        return true
    }
} 