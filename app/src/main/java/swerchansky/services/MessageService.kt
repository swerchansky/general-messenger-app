package swerchansky.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import swerchansky.Constants.MESSAGES_INTERVAL
import swerchansky.Constants.NEW_MESSAGES
import swerchansky.Constants.SEND_MESSAGE
import swerchansky.Constants.SEND_MESSAGE_FAILED
import swerchansky.messenger.Data
import swerchansky.messenger.Message
import swerchansky.messenger.Text
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Semaphore


class MessageService : Service() {
   companion object {
      const val TAG = "MessageService"
   }

   private val semaphoreReceive = Semaphore(1, true)
   private val receiveMessageHandler = Handler(Looper.myLooper()!!)

   private var messageReceiver = object : Runnable {
      @Synchronized
      override fun run() {
         try {
            val thread = Thread {
               semaphoreReceive.acquire()
               val newMessages = try {
                  getMoreMessages((messages.size + 1).toLong())
               } catch (e: Exception) {
                  mutableListOf()
               }
               getImages(newMessages)
               messages += newMessages
               sendIntent(NEW_MESSAGES)
               semaphoreReceive.release()
            }

            thread.start()
         } finally {
            receiveMessageHandler.postDelayed(this, MESSAGES_INTERVAL)
         }
      }
   }

   private val sendMessageListener: BroadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent) {
         when (intent.getIntExtra("type", -1)) {
            SEND_MESSAGE -> prepareAndSendTextMessage(intent.getStringExtra("text") ?: "")
         }
      }
   }

   val messages: MutableList<Message> = mutableListOf()

   override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
      startMessageReceiver()
      LocalBroadcastManager.getInstance(this)
         .registerReceiver(sendMessageListener, IntentFilter("mainActivity"))
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

   private fun prepareAndSendTextMessage(
      text: String,
      from: String = "swerchansky",
      to: String = "1@ch"
   ) {
      if (text.isNotEmpty()) {
         val mapper = JsonMapper
            .builder()
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build()
            .registerModule(KotlinModule.Builder().build())
         val message = Message(
            from,
            to,
            Data(Text = Text(text)),
            Date().time.toString()
         )
         val json = mapper.writeValueAsString(message).replaceFirst("text", "Text")
         Thread {
            sendTextMessage(json)
         }.start()
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
      }

      connection.setFixedLengthStreamingMode(outLength)
      connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
      connection.connect()
      connection.outputStream.use { os -> os.write(message) }
      Log.i(TAG, "send message response code: ${connection.responseCode}")
      if (connection.responseCode != 200) {
         sendIntent(SEND_MESSAGE_FAILED, connection.responseCode.toString())
      }
   }

   private fun getImages(messages: MutableList<Message>) {
      messages.forEach { message ->
         if (message.data.Image?.link?.isNotEmpty() == true) {
            message.data.Image.bitmap = downloadThumbImage(message.data.Image.link)
         }
      }
   }

   private fun downloadThumbImage(link: String): Bitmap {
      val url = URL("http://213.189.221.170:8008/thumb/$link")
      val stream = url.openStream()
      return BitmapFactory.decodeStream(stream)
   }

   private fun getMoreMessages(from: Long, count: Long = 10): MutableList<Message> {
      val url = messagesURLWithParams(
         mapOf(
            "limit" to count.toString(),
            "lastKnownId" to from.toString()
         )
      )
      val httpURLConnection = url.openConnection() as HttpURLConnection
      httpURLConnection.requestMethod = "GET"
      val response = httpURLConnection.inputStream.bufferedReader().readText()
      val mapper = JsonMapper
         .builder()
         .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
         .build()
         .registerModule(KotlinModule.Builder().build())

      return mapper.readValue(response)
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
      val intent = Intent("messageService")
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