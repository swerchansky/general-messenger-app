package swerchansky.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.nio.charset.StandardCharsets

class NetworkHelper {

   fun getLastMessages(from: Long, count: Long = 100): String {
      val url = messagesURLWithParams(
         mapOf(
            "limit" to count.toString(),
            "lastKnownId" to from.toString()
         )
      )
      val httpURLConnection = url.openConnection() as HttpURLConnection
      httpURLConnection.requestMethod = "GET"

      val response: String
      httpURLConnection.inputStream.use { inputStream ->
         inputStream.bufferedReader().use {
            response = it.readText()
         }
      }

      httpURLConnection.disconnect()
      return response
   }

   private fun messagesURLWithParams(
      parameters: Map<String, String> = emptyMap(),
      path: String = "http://213.189.221.170:8008/1ch"
   ): URL {
      var fullPath = path
      if (parameters.isNotEmpty()) {
         fullPath += "?"
         parameters.forEach { (key, value) ->
            fullPath += "$key=$value&"
         }
      }
      return URL(fullPath)
   }

   fun downloadFullImage(link: String): Bitmap {
      val url = URL("http://213.189.221.170:8008/img/$link")
      return downloadImage(url)
   }

   fun downloadThumbImage(link: String): Bitmap {
      val url = URL("http://213.189.221.170:8008/thumb/$link")
      return downloadImage(url)
   }

   private fun downloadImage(url: URL): Bitmap {
      val photo = url.openStream().use {
         BitmapFactory.decodeStream(it)
      }
      return photo
   }

   fun sendTextMessage(json: String): Int {
      val url = URL("http://213.189.221.170:8008/1ch")
      val connection = url.openConnection() as HttpURLConnection
      val message = json.toByteArray(StandardCharsets.UTF_8)
      connection.apply {
         requestMethod = "POST"
         doInput = true
         connectTimeout = 2000
      }

      connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
      connection.connect()
      connection.outputStream.use { os -> os.write(message) }
      val responseCode = connection.responseCode
      connection.disconnect()
      return responseCode
   }

   fun sendImageMessage(file: File, code: String): Int {
      val url = URL("http://213.189.221.170:8008/1ch")
      val connection = url.openConnection() as HttpURLConnection
      connection.apply {
         requestMethod = "POST"
         doInput = true
         doOutput = true
         connectTimeout = 2000
      }

      val boundary = "------$code------"
      connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

      val crlf = "\r\n"
      val json = "{\"from\":\"swerchansky\"}"
      val outputStream = connection.outputStream
      val outputStreamWriter = OutputStreamWriter(outputStream)
      outputStream.use {
         outputStreamWriter.use {
            with(it) {
               append("--").append(boundary).append(crlf)
               append("Content-Disposition: form-data; name=\"json\"").append(crlf)
               append("Content-Type: application/json; charset=utf-8").append(crlf)
               append(crlf)
               append(json).append(crlf)
               flush()
               appendFile(file, boundary, outputStream)
               append(crlf)
               append("--").append(boundary).append("--").append(crlf)
            }
         }
      }
      val responseCode = connection.responseCode
      connection.disconnect()
      return responseCode
   }

   private fun OutputStreamWriter.appendFile(
      file: File,
      boundary: String,
      outputStream: OutputStream,
      crlf: String = "\r\n"
   ) {
      val contentType = URLConnection.guessContentTypeFromName(file.name)
      val fis = FileInputStream(file)
      fis.use {
         append("--").append(boundary).append(crlf)
         append("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"")
         append(crlf)
         append("Content-Type: $contentType").append(crlf)
         append("Content-Length: ${file.length()}").append(crlf)
         append("Content-Transfer-Encoding: binary").append(crlf)
         append(crlf)
         flush()

         val buffer = ByteArray(4096)

         var n: Int
         while (fis.read(buffer).also { n = it } != -1) {
            outputStream.write(buffer, 0, n)
         }
         outputStream.flush()
         append(crlf)
         flush()
      }
   }
}