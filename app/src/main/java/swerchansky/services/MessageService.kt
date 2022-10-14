package swerchansky.services

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import swerchansky.messenger.Message
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Semaphore


class MessageService : Service() {
   companion object {
      private const val NEW_MESSAGES = 1
   }

   private val semaphore = Semaphore(1, true)
   private val receiveMessageHandler = Handler(Looper.myLooper()!!)
   private var messagesInterval = 2000L

   private var messageReceiver = object : Runnable {
      @Synchronized
      override fun run() {
         try {
            val thread = synchronized(messages) {
               Thread {
                  semaphore.acquire()
                  val newMessages = getMoreMessages((messages.size + 1).toLong())
                  getImages(newMessages)
                  messages += newMessages
                  sendMessage()
                  semaphore.release()
                  println("ABOBA")
               }
            }
            thread.start()
         } finally {
            receiveMessageHandler.postDelayed(this, messagesInterval)
         }
      }
   }

   val messages: MutableList<Message> = mutableListOf()

   override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
      startMessageReceiver()
      return super.onStartCommand(intent, flags, startId)
   }

   override fun onBind(intent: Intent?): IBinder {
      return MyBinder()
   }

   override fun onUnbind(intent: Intent?): Boolean {
      stopMessageReceiver()
      return super.onUnbind(intent)
   }

   inner class MyBinder : Binder() {
      fun getService() = this@MessageService
   }

   private fun sendMessage() {
      val intent = Intent("messageService")
      intent.putExtra("type", NEW_MESSAGES)
      LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
   }

   private fun getImages(messages: MutableList<Message>) {
      messages.forEach { message ->
         if (message.data.image.link.isNotEmpty()) {
            message.data.image.bitmap = downloadThumbImage(message.data.image.link)
         }
      }
   }

   private fun downloadThumbImage(link: String): Bitmap {
      val url = URL("http://213.189.221.170:8008/thumb/$link")
      val stream = url.openStream()
      return BitmapFactory.decodeStream(stream)
   }

   private fun getMoreMessages(from: Long, count: Long = 20): MutableList<Message> {
      val url = urlWithParams(
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

   private fun urlWithParams(
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

   private fun startMessageReceiver() {
      messageReceiver.run()
   }

   private fun stopMessageReceiver() {
      receiveMessageHandler.removeCallbacks(messageReceiver)
   }
}