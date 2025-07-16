package com.alarm.tool

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.alarm.tool.dpToPx

class AlarmSettingsActivity : AppCompatActivity() {

    private lateinit var timePicker: TimePicker
    private lateinit var cbSun: CheckBox
    private lateinit var cbMon: CheckBox
    private lateinit var cbTue: CheckBox
    private lateinit var cbWed: CheckBox
    private lateinit var cbThu: CheckBox
    private lateinit var cbFri: CheckBox
    private lateinit var cbSat: CheckBox
    private lateinit var rvTaskTypes: RecyclerView
    private lateinit var tvRingtoneName: TextView
    private lateinit var switchVibrate: SwitchCompat
    private lateinit var btnSaveAlarm: Button
    private lateinit var ivBack: ImageView
    private lateinit var rlRingtoneSelection: RelativeLayout

    private lateinit var alarmViewModel: AlarmViewModel
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var taskTypeAdapter: TaskTypeAdapter
    private lateinit var permissionManager: PermissionManager
    private var selectedRingtoneUri: Uri? = null
    private var currentAlarm: Alarm? = null

    private val RINGTONE_PICKER_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_settings)

        initViews()
        alarmViewModel = ViewModelProvider(this).get(AlarmViewModel::class.java)
        alarmScheduler = AlarmScheduler(applicationContext)
        permissionManager = PermissionManager(this)

        val alarmId = intent.getIntExtra("alarm_id", -1)
        if (alarmId != -1) {
            lifecycleScope.launch(Dispatchers.IO) {
                val alarm = alarmViewModel.getAlarmById(alarmId)
                withContext(Dispatchers.Main) {
                    alarm?.let { bindAlarmData(it) }
                }
            }
        }

        setupListeners()
        setupTaskTypeRecyclerView()
    }

    private fun initViews() {
        timePicker = findViewById(R.id.time_picker)
        timePicker.setIs24HourView(true) // Use 24-hour format as common for alarms

        cbSun = findViewById(R.id.cb_sun)
        cbMon = findViewById(R.id.cb_mon)
        cbTue = findViewById(R.id.cb_tue)
        cbWed = findViewById(R.id.cb_wed)
        cbThu = findViewById(R.id.cb_thu)
        cbFri = findViewById(R.id.cb_fri)
        cbSat = findViewById(R.id.cb_sat)
        rvTaskTypes = findViewById(R.id.rv_task_types)
        tvRingtoneName = findViewById(R.id.tv_ringtone_name)
        switchVibrate = findViewById(R.id.switch_vibrate)
        btnSaveAlarm = findViewById(R.id.btn_save_alarm)
        ivBack = findViewById(R.id.iv_back_button)
        rlRingtoneSelection = findViewById(R.id.rl_ringtone_selection)
    }

    private fun setupListeners() {
        ivBack.setOnClickListener { finish() }

        rlRingtoneSelection.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.select_ringtone))
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                selectedRingtoneUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it) }
            }
            startActivityForResult(intent, RINGTONE_PICKER_REQUEST)
        }

        btnSaveAlarm.setOnClickListener { saveAlarm() }
    }

    private fun setupTaskTypeRecyclerView() {
        taskTypeAdapter = TaskTypeAdapter { taskType ->
            // No additional action needed here, selection is handled internally by adapter
        }
        rvTaskTypes.adapter = taskTypeAdapter
        rvTaskTypes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun bindAlarmData(alarm: Alarm) {
        currentAlarm = alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.hour = alarm.hour
            timePicker.minute = alarm.minute
        } else {
            @Suppress("DEPRECATION")
            timePicker.currentHour = alarm.hour
            @Suppress("DEPRECATION")
            timePicker.currentMinute = alarm.minute
        }

        cbSun.isChecked = alarm.daysOfWeek.contains(0)
        cbMon.isChecked = alarm.daysOfWeek.contains(1)
        cbTue.isChecked = alarm.daysOfWeek.contains(2)
        cbWed.isChecked = alarm.daysOfWeek.contains(3)
        cbThu.isChecked = alarm.daysOfWeek.contains(4)
        cbFri.isChecked = alarm.daysOfWeek.contains(5)
        cbSat.isChecked = alarm.daysOfWeek.contains(6)

        taskTypeAdapter.selectedTaskType = alarm.taskType

        alarm.ringtoneUri?.let { uriString ->
            selectedRingtoneUri = Uri.parse(uriString)
            tvRingtoneName.text = RingtoneManager.getRingtone(this, selectedRingtoneUri)?.getTitle(this)
        } ?: run { // No ringtone selected, show default
            tvRingtoneName.text = getString(R.string.default_ringtone)
        }

        switchVibrate.isChecked = alarm.vibrate
    }

    private fun saveAlarm() {
        Log.d("AlarmSettings", "Starting save alarm process")
        
        // 检查所有必需的权限
        val requiredPermissions = setOf(
            PermissionType.EXACT_ALARM,
            PermissionType.OVERLAY,
            PermissionType.AUTOSTART,
            PermissionType.BATTERY_OPTIMIZATION
        )

        permissionManager.requestPermissionsIfNeeded(
            permissionTypes = requiredPermissions,
            onAllGranted = {
                Log.d("AlarmSettings", "All permissions granted")
                saveAlarmInternal()
            },
            onSomeGranted = { granted ->
                Log.w("AlarmSettings", "Some permissions granted: $granted")
                if (granted.contains(PermissionType.EXACT_ALARM)) {
                    saveAlarmInternal()
                } else {
                    Toast.makeText(this, "无法保存闹钟：缺少必要权限", Toast.LENGTH_LONG).show()
                }
            },
            onAllDenied = {
                Log.e("AlarmSettings", "All permissions denied")
                Toast.makeText(this, "无法保存闹钟：所有权限都被拒绝", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun saveAlarmInternal() {
        try {
            Log.d("AlarmSettings", "Starting to save alarm")
            val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) timePicker.hour else @Suppress("DEPRECATION") timePicker.currentHour
            val minute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) timePicker.minute else @Suppress("DEPRECATION") timePicker.currentMinute

            Log.d("AlarmSettings", "Time selected: $hour:$minute")

            val selectedDays = mutableListOf<Int>()
            if (cbSun.isChecked) selectedDays.add(0)
            if (cbMon.isChecked) selectedDays.add(1)
            if (cbTue.isChecked) selectedDays.add(2)
            if (cbWed.isChecked) selectedDays.add(3)
            if (cbThu.isChecked) selectedDays.add(4)
            if (cbFri.isChecked) selectedDays.add(5)
            if (cbSat.isChecked) selectedDays.add(6)

            Log.d("AlarmSettings", "Selected days: $selectedDays")

            if (selectedDays.isEmpty()) {
                Log.w("AlarmSettings", "No days selected")
                Toast.makeText(this, "请至少选择一天", Toast.LENGTH_SHORT).show()
                return
            }

            val taskType = taskTypeAdapter.selectedTaskType
            Log.d("AlarmSettings", "Selected task type: $taskType")

            val ringtone = selectedRingtoneUri?.toString() ?: "content://settings/system/alarm_alert"
            Log.d("AlarmSettings", "Selected ringtone: $ringtone")

            val vibrate = switchVibrate.isChecked
            Log.d("AlarmSettings", "Vibrate: $vibrate")

            val newAlarm = currentAlarm?.copy(
                hour = hour,
                minute = minute,
                daysOfWeek = selectedDays,
                taskType = taskType,
                ringtoneUri = ringtone,
                vibrate = vibrate,
                isEnabled = true
            ) ?: Alarm(
                hour = hour,
                minute = minute,
                daysOfWeek = selectedDays,
                taskType = taskType,
                ringtoneUri = ringtone,
                vibrate = vibrate,
                isEnabled = true
            )

            Log.d("AlarmSettings", "Alarm object created: $newAlarm")

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    if (currentAlarm == null) {
                        Log.d("AlarmSettings", "Inserting new alarm")
                        val newAlarmId = alarmViewModel.insert(newAlarm)
                        Log.d("AlarmSettings", "New alarm inserted with ID: $newAlarmId")
                        val alarmToSchedule = newAlarm.copy(id = newAlarmId.toInt())
                        alarmScheduler.scheduleAlarm(alarmToSchedule)
                        Log.d("AlarmSettings", "New alarm scheduled")
                    } else {
                        Log.d("AlarmSettings", "Updating existing alarm")
                        alarmViewModel.update(newAlarm)
                        Log.d("AlarmSettings", "Alarm updated")
                        alarmScheduler.cancelAlarm(currentAlarm!!)
                        Log.d("AlarmSettings", "Old alarm cancelled")
                        alarmScheduler.scheduleAlarm(newAlarm)
                        Log.d("AlarmSettings", "New alarm scheduled")
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AlarmSettingsActivity, "闹钟已保存！", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e("AlarmSettings", "Error saving alarm", e)
                    withContext(Dispatchers.Main) {
                        val errorMessage = when {
                            e.message?.contains("UNIQUE constraint") == true -> "已存在相同时间的闹钟"
                            e.message?.contains("database") == true -> "数据库错误"
                            else -> "保存闹钟失败：${e.message}"
                        }
                        Toast.makeText(this@AlarmSettingsActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmSettings", "Error in saveAlarmInternal", e)
            Toast.makeText(this, "保存闹钟时发生错误：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RINGTONE_PICKER_REQUEST && resultCode == Activity.RESULT_OK) {
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            selectedRingtoneUri = uri
            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(this, uri)
                tvRingtoneName.text = ringtone?.getTitle(this) ?: getString(R.string.no_ringtone_selected)
            } else {
                tvRingtoneName.text = getString(R.string.no_ringtone_selected)
            }
        }
    }
} 