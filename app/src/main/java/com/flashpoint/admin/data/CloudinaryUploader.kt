package com.flashpoint.admin.data

import android.content.Context
import android.net.Uri
import com.flashpoint.admin.core.UploadException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object CloudinaryConfig {
    const val CLOUD_NAME = "jylepeqy"
    const val UPLOAD_PRESET = "flashpoint_upload"
}

data class UploadResult(val url: String, val publicId: String, val width: Int, val height: Int, val bytes: Long)

/** Unsigned client-side upload. No API secret is used or stored in the app. */
object CloudinaryUploader {
    private const val MAX_BYTES = 10L * 1024 * 1024

    suspend fun upload(context: Context, uri: Uri, onProgress: (Int) -> Unit): UploadResult =
        withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            val mime = cr.getType(uri).orEmpty()
            if (!mime.startsWith("image/")) throw UploadException("Unsupported file. Please choose an image.")
            val size = runCatching { cr.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L }.getOrDefault(-1L)
            if (size > MAX_BYTES) throw UploadException("Image is too large. Maximum size is 10 MB.")
            val data = cr.openInputStream(uri)?.use { it.readBytes() }
                ?: throw UploadException("Could not read the selected file.")
            if (data.isEmpty() || data.size > MAX_BYTES) throw UploadException("Invalid or oversized image.")

            val boundary = "----FlashPoint${System.currentTimeMillis()}"
            val head = ("--$boundary\r\nContent-Disposition: form-data; name=\"upload_preset\"\r\n\r\n" +
                "${CloudinaryConfig.UPLOAD_PRESET}\r\n--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"upload\"\r\n" +
                "Content-Type: $mime\r\n\r\n").toByteArray()
            val tail = "\r\n--$boundary--\r\n".toByteArray()

            val conn = URL("https://api.cloudinary.com/v1_1/${CloudinaryConfig.CLOUD_NAME}/image/upload")
                .openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.setFixedLengthStreamingMode(head.size + data.size + tail.size)
                conn.outputStream.use { out ->
                    out.write(head)
                    var off = 0
                    while (off < data.size) {
                        ensureActive() // lets the user cancel mid-upload
                        val n = minOf(16_384, data.size - off)
                        out.write(data, off, n)
                        off += n
                        onProgress((off * 100L / data.size).toInt())
                    }
                    out.write(tail)
                }
                val ok = conn.responseCode in 200..299
                val text = (if (ok) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
                if (!ok) throw UploadException("Image upload failed. Please try again.")
                val j = JSONObject(text)
                UploadResult(j.getString("secure_url"), j.optString("public_id"),
                    j.optInt("width"), j.optInt("height"), j.optLong("bytes"))
            } finally {
                conn.disconnect()
            }
        }
}
