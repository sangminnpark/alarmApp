package com.example.activity_mainxml.util

import android.content.Context
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream

object FileUtil {
    fun saveTempFile(
        body: ResponseBody,
        prefix: String,
        extension: String,
        context: Context
    ): File? {
        return try {
            val fileName = "${prefix}${System.currentTimeMillis()}${extension}"
            val outputFile = File(context.filesDir, fileName)
            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
            outputFile
        } catch (e: Exception) {
            null
        }
    }
}
