package com.example.activity_mainxml.util

import android.content.Context
import android.net.Uri
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream

object FileUtil {
    fun copyUriToTempFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.wav")
        inputStream?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    fun saveTempFile(body: ResponseBody, prefix: String, suffix: String, context: Context): File? {
        return try {
            val file = File.createTempFile(prefix, suffix, context.cacheDir)
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            null
        }
    }
}
