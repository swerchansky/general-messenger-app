package swerchansky.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import swerchansky.Constants.ERROR
import swerchansky.Constants.NEW_MESSAGES
import swerchansky.Constants.SEND_IMAGE
import swerchansky.Constants.SEND_IMAGE_FAILED
import swerchansky.Constants.SEND_MESSAGE
import swerchansky.Constants.SEND_MESSAGE_FAILED
import swerchansky.Constants.SERVER_ERROR
import swerchansky.messenger.Data
import swerchansky.messenger.Message
import swerchansky.messenger.Text
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Semaphore


class MessageService : Service() {
   companion object {
      const val TAG = "MessageService"
      const val MAIN_ACTIVITY_TAG = "MainActivity"
   }

   private var messagesInterval = 1500L
   private val semaphoreReceive = Semaphore(1, true)
   private val receiveMessageHandler = Handler(Looper.myLooper()!!)
   private val receiveMapper = JsonMapper
      .builder()
      .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
      .build()
      .registerModule(KotlinModule.Builder().build())
   private val sendMapper = JsonMapper
      .builder()
      .serializationInclusion(JsonInclude.Include.NON_NULL)
      .build()
      .registerModule(KotlinModule.Builder().build())

   private var messageReceiver = object : Runnable {
      @Synchronized
      override fun run() {
         try {
            updateMessages()
         } finally {
            receiveMessageHandler.postDelayed(this, messagesInterval)
         }
      }
   }

   private val sendMessageListener: BroadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent) {
         when (intent.getIntExtra("type", -1)) {
            SEND_MESSAGE -> prepareAndSendTextMessage(intent.getStringExtra("text") ?: "")
            SEND_IMAGE -> prepareAndSendImageMessage(Uri.parse(intent.getStringExtra("uri")) ?: Uri.EMPTY)
         }
      }
   }

   val messages: MutableList<Message> = mutableListOf()

   override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
      startMessageReceiver()
      LocalBroadcastManager.getInstance(this)
         .registerReceiver(sendMessageListener, IntentFilter(MAIN_ACTIVITY_TAG))
      return super.onStartCommand(intent, flags, startId)
   }

   override fun onBind(intent: Intent?): IBinder {
      return MyBinder()
   }

   override fun onUnbind(intent: Intent?): Boolean {
      stopMessageReceiver()
      LocalBroadcastManager.getInstance(this).unregisterReceiver(sendMessageListener)
      return super.onUnbind(intent)
   }

   inner class MyBinder : Binder() {
      fun getService() = this@MessageService
   }

   fun getFullImage(position: Int): Bitmap? {
      val message = messages[position]
      var image: Bitmap? = null
      val thread = Thread {
         image = downloadFullImage(message.data.Image!!.link)
      }
      thread.start()
      synchronized(thread) {
         thread.join()
      }
      return image
   }

   private fun updateMessages() {
      val thread = Thread {
         semaphoreReceive.acquire()
         val newMessages = try {
            getMoreMessages((messages.size + 1).toLong())
         } catch (e: Exception) {
            mutableListOf()
         }
         messagesInterval = if (newMessages.isEmpty()) {
            10000L
         } else {
            1500L
         }
         getImages(newMessages)
         messages += newMessages
         sendIntent(NEW_MESSAGES)
         semaphoreReceive.release()
      }

      thread.start()
   }

   private fun prepareAndSendImageMessage(uri: Uri) {
      if (uri == Uri.EMPTY) {
         sendIntent(SEND_IMAGE_FAILED, "Uri is empty")
         return
      }

      val image = getImageFromStorage(uri)

      if (image == null) {
         sendIntent(SEND_IMAGE_FAILED, "Image is null")
         return
      }

      val code = Date().time.toString()
      val file = try {
         getTempFile(image, code)
      } catch (e: Exception) {
         sendIntent(SEND_IMAGE_FAILED, "Can't create temp file")
         return
      }
      Thread {
         try {
            sendImageMessage(file, code)
         } catch (e: Exception) {
            sendIntent(SERVER_ERROR)
         } finally {
            file.delete()
         }
      }.start()
   }

   private fun sendImageMessage(file: File, code: String) {
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
      if (connection.responseCode != HttpURLConnection.HTTP_OK) {
         sendIntent(SEND_IMAGE_FAILED, "Server error")
      }
      connection.disconnect()
      updateMessages()
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

   private fun getTempFile(image: Bitmap, code: String): File {
      val file = File(this.cacheDir, "$code.png")
      file.createNewFile()
      val bos = ByteArrayOutputStream()
      image.compress(Bitmap.CompressFormat.PNG, 0, bos)
      val bitmapData = bos.toByteArray()
      val fos = FileOutputStream(file)
      fos.write(bitmapData)
      fos.flush()
      fos.close()
      return file
   }

   private fun getImageFromStorage(uri: Uri): Bitmap? {
      return try {
         if (Build.VERSION.SDK_INT < 28) {
            MediaStore.Images.Media.getBitmap(
               this.contentResolver,
               uri
            )
         } else {
            val source = ImageDecoder.createSource(this.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
         }
      } catch (e: Exception) {
         null
      }
   }

   private fun prepareAndSendTextMessage(
      text: String,
      from: String = "swerchansky",
      to: String = "1@ch"
   ) {
      if (text.isNotEmpty()) {
         val message = Message(
            from,
            to,
            Data(Text = Text(text)),
            Date().time.toString()
         )
         val json = sendMapper.writeValueAsString(message).replaceFirst("text", "Text")
         Thread {
            try {
               sendTextMessage(json)
            } catch (e: Exception) {
               sendIntent(SERVER_ERROR)
            }
         }.start()
      } else {
         sendIntent(ERROR, "Message can't be empty")
      }
   }

   private fun sendTextMessage(json: String) {
      val url = URL("http://213.189.221.170:8008/1ch")
      val connection = url.openConnection() as HttpURLConnection
      val message = json.toByteArray(StandardCharsets.UTF_8)
      val outLength = message.size
      connection.apply {
         requestMethod = "POST"
         doInput = true
         connectTimeout = 2000
      }

      connection.setFixedLengthStreamingMode(outLength)
      connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
      connection.connect()
      connection.outputStream.use { os -> os.write(message) }
      Log.i(TAG, "send message response code: ${connection.responseCode}")
      if (connection.responseCode != 200) {
         sendIntent(SEND_MESSAGE_FAILED, connection.responseCode.toString())
      }
      connection.disconnect()
      updateMessages()
   }

   private fun getImages(messages: MutableList<Message>) {
      messages.forEach { message ->
         if (message.data.Image?.link?.isNotEmpty() == true) {
            message.data.Image.bitmap = downloadThumbImage(message.data.Image.link)
         }
      }
   }

   private fun downloadFullImage(link: String): Bitmap {
      val url = URL("http://213.189.221.170:8008/img/$link")
      return downloadImage(url)
   }

   private fun downloadThumbImage(link: String): Bitmap {
      val url = URL("http://213.189.221.170:8008/thumb/$link")
      return downloadImage(url)
   }

   private fun downloadImage(url: URL): Bitmap {
      val photo = url.openStream().use {
         BitmapFactory.decodeStream(it)
      }
      return photo
   }

   private fun getMoreMessages(from: Long, count: Long = 100): MutableList<Message> {
      val url = messagesURLWithParams(
         mapOf(
            "limit" to count.toString(),
            "lastKnownId" to from.toString()
         )
      )
      val httpURLConnection = url.openConnection() as HttpURLConnection
      httpURLConnection.requestMethod = "GET"
      val response = httpURLConnection.inputStream.bufferedReader().readText()

      httpURLConnection.disconnect()
      return receiveMapper.readValue(response)
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

   private fun sendIntent(type: Int, text: String = "") {
      val intent = Intent(TAG)
      intent.putExtra("type", type)
      intent.putExtra("text", text)
      LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
   }

   private fun startMessageReceiver() {
      messageReceiver.run()
   }

   private fun stopMessageReceiver() {
      receiveMessageHandler.removeCallbacks(messageReceiver)
   }
}