package com.alarm.tool

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.Activity
import android.content.Context
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import java.io.File
import android.util.Log

class AboutActivity : AppCompatActivity() {

    private lateinit var tvAuthorInfo: TextView
    private lateinit var tvVersionInfo: TextView
    private lateinit var tvGithubLink: TextView
    private lateinit var btnCheckForUpdates: Button
    private lateinit var btnBackup: Button
    private lateinit var btnRestore: Button
    private lateinit var ivBack: ImageView
    private lateinit var alarmRepository: AlarmRepository
    private val TAG = "AboutActivity"

    private val restoreFilePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                restoreFromUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        alarmRepository = AlarmRepository(AlarmDatabase.getDatabase(this).alarmDao())

        initViews()
        setupListeners()
        displayAppInfo()
    }

    private fun initViews() {
        tvAuthorInfo = findViewById(R.id.tv_author_info)
        tvVersionInfo = findViewById(R.id.tv_version_info)
        tvGithubLink = findViewById(R.id.tv_github_link)
        btnCheckForUpdates = findViewById(R.id.btn_check_for_updates)
        btnBackup = findViewById(R.id.btn_backup)
        btnRestore = findViewById(R.id.btn_restore)
        ivBack = findViewById(R.id.iv_about_back_button)
    }

    private fun setupListeners() {
        ivBack.setOnClickListener { finish() }

        tvGithubLink.setOnClickListener {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(Constants.GITHUB_URL))
                startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.no_browser_toast), Toast.LENGTH_SHORT).show()
            }
        }

        btnCheckForUpdates.setOnClickListener { 
            checkForUpdates()
        }

        btnBackup.setOnClickListener {
            createBackup()
        }

        btnRestore.setOnClickListener {
            showRestoreDialog()
        }
    }

    private fun displayAppInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            tvVersionInfo.text = getString(R.string.version_info, packageInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            tvVersionInfo.text = getString(R.string.version_info, "unknown")
        }
    }

    private fun createBackup() {
        lifecycleScope.launch {
            try {
                val result = alarmRepository.createBackup(getExternalFilesDir(null)!!)
                when (result) {
                    is BackupResult.Success -> {
                        showSuccessDialog(
                            "备份成功",
                            "备份文件已保存到：${result.filePath}"
                        )
                    }
                    is BackupResult.Error -> {
                        showErrorDialog("备份失败", result.message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating backup", e)
                showErrorDialog("备份失败", e.message ?: "未知错误")
            }
        }
    }

    private fun showRestoreDialog() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/json"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            restoreFilePicker.launch(intent)
        } catch (e: Exception) {
            showErrorDialog("错误", "无法打开文件选择器")
        }
    }

    private fun restoreFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                // 复制文件到临时目录
                val tempFile = File(cacheDir, "temp_backup.json")
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 导入数据
                when (val result = alarmRepository.importAlarms(tempFile)) {
                    is ImportResult.Success -> {
                        showSuccessDialog(
                            "恢复成功",
                            "已恢复 ${result.count} 个闹钟"
                        )
                    }
                    is ImportResult.Error -> {
                        showErrorDialog("恢复失败", result.message)
                    }
                }

                // 清理临时文件
                tempFile.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring backup", e)
                showErrorDialog("恢复失败", e.message ?: "未知错误")
            }
        }
    }

    private fun showSuccessDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun checkForUpdates() {
        btnCheckForUpdates.isEnabled = false
        btnCheckForUpdates.text = getString(R.string.update_checking)

        Handler(Looper.getMainLooper()).postDelayed({
            btnCheckForUpdates.isEnabled = true
            btnCheckForUpdates.text = getString(R.string.check_for_updates)
            Toast.makeText(this, getString(R.string.update_latest), Toast.LENGTH_SHORT).show()
        }, 1500)
    }
} 