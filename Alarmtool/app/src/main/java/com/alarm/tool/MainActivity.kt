package com.alarm.tool

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.alarm.tool.dpToPx

class MainActivity : AppCompatActivity() {

    private lateinit var alarmViewModel: AlarmViewModel
    private lateinit var alarmAdapter: AlarmAdapter
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        alarmViewModel = ViewModelProvider(this).get(AlarmViewModel::class.java)
        alarmScheduler = AlarmScheduler(applicationContext)
        permissionManager = PermissionManager(this)

        setupPermissions()
        setupRecyclerView()
        setupFab()
        setupSettingsMenu()

        // Observe alarms from ViewModel
        alarmViewModel.allAlarms.observe(this) { alarms ->
            alarmAdapter.submitList(alarms)
        }
    }

    private fun setupPermissions() {
        // 请求所有必需的权限
        val requiredPermissions = setOf(
            PermissionType.EXACT_ALARM,
            PermissionType.OVERLAY,
            PermissionType.AUTOSTART,
            PermissionType.BATTERY_OPTIMIZATION,
            PermissionType.CAMERA
        )

        permissionManager.requestPermissionsIfNeeded(
            permissionTypes = requiredPermissions,
            onAllGranted = {
                // 所有权限都已授予，可以继续
            },
            onSomeGranted = { granted ->
                // 部分权限已授予
                if (!granted.contains(PermissionType.EXACT_ALARM)) {
                    Toast.makeText(this, "精确闹钟权限被拒绝，闹钟可能无法准时响铃！", Toast.LENGTH_LONG).show()
                }
                if (!granted.contains(PermissionType.CAMERA)) {
                    Toast.makeText(this, "相机权限被拒绝，部分任务功能将受限！", Toast.LENGTH_LONG).show()
                }
            },
            onAllDenied = {
                // 所有权限都被拒绝
                Toast.makeText(this, "重要权限被拒绝，应用可能无法正常工作！", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.rv_alarms)
        alarmAdapter = AlarmAdapter(
            onItemClick = { alarm ->
                // Handle alarm click (edit alarm)
                val intent = Intent(this, AlarmSettingsActivity::class.java)
                intent.putExtra("alarm_id", alarm.id)
                startActivity(intent)
            },
            onItemLongClick = { alarm ->
                // Handle alarm long click (delete alarm)
                AlertDialog.Builder(this)
                    .setTitle("删除闹钟")
                    .setMessage("确定要删除此闹钟吗？")
                    .setPositiveButton("删除") { dialog, _ ->
                        alarmViewModel.delete(alarm)
                        alarmScheduler.cancelAlarm(alarm)
                        Toast.makeText(this, "闹钟已删除", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            },
            onToggle = { alarm, isEnabled ->
                // Handle alarm toggle switch
                val updatedAlarm = alarm.copy(isEnabled = isEnabled)
                alarmViewModel.update(updatedAlarm)
                if (isEnabled) {
                    alarmScheduler.scheduleAlarm(updatedAlarm)
                    Toast.makeText(this, "闹钟已开启", Toast.LENGTH_SHORT).show()
                } else {
                    alarmScheduler.cancelAlarm(updatedAlarm)
                    Toast.makeText(this, "闹钟已关闭", Toast.LENGTH_SHORT).show()
                }
            }
        )
        recyclerView.adapter = alarmAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupFab() {
        val fabAddAlarm = findViewById<FloatingActionButton>(R.id.fab_add_alarm)
        fabAddAlarm.setOnClickListener {
            val intent = Intent(this, AlarmSettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupSettingsMenu() {
        val settingsMenu = findViewById<ImageView>(R.id.iv_settings_menu)
        settingsMenu.setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // 检查权限状态
        if (!permissionManager.hasAllRequiredPermissions()) {
            setupPermissions()
        }
    }
} 