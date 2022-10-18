package swerchansky.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import swerchansky.Constants
import swerchansky.Constants.ERROR
import swerchansky.Constants.NEW_MESSAGES
import swerchansky.Constants.SEND_IMAGE
import swerchansky.Constants.SEND_IMAGE_FAILED
import swerchansky.Constants.SEND_MESSAGE
import swerchansky.Constants.SERVER_ERROR
import swerchansky.db.databases.MessageDatabase
import swerchansky.db.entities.MessageEntity
import swerchansky.messenger.Data
import swerchansky.messenger.Image
import swerchansky.messenger.Message
import swerchansky.messenger.Text
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.util.*
import java.util.concurrent.Semaphore


class MessageService : Service() {
   companion object {
      const val TAG = "MessageService"
      const val MAIN_ACTIVITY_TAG = "MainActivity"
   }

   private val network = NetworkHelper()
   private val messagesInterval = 5000L
   private val semaphoreReceive = Semaphore(1, true)
   private val receiveMessageHandler = Handler(Looper.myLooper()!!)
   private val messagesDatabase by lazy { MessageDatabase.getDatabase(this).messagesDAO() }
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
            SEND_IMAGE -> prepareAndSendImageMessage(
               Uri.parse(intent.getStringExtra("uri")) ?: Uri.EMPTY
            )
         }
      }
   }

   val messages: MutableList<Message> = mutableListOf()

   override fun onCreate() {
      super.onCreate()
      loadMessages()
      LocalBroadcastManager.getInstance(this)
         .registerReceiver(sendMessageListener, IntentFilter(MAIN_ACTIVITY_TAG))
   }

   override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

   override fun onBind(intent: Intent?): IBinder {
      return MyBinder()
   }

   override fun onUnbind(intent: Intent?): Boolean {
      return super.onUnbind(intent)
   }

   override fun onDestroy() {
      super.onDestroy()
      stopMessageReceiver()
      LocalBroadcastManager.getInstance(this).unregisterReceiver(sendMessageListener)
   }

   inner class MyBinder : Binder() {
      fun getService() = this@MessageService
   }

   fun getFullImage(position: Int): Bitmap? {
      val message = messages[position]
      var image: Bitmap? = null
      val thread = Thread {
         image = network.downloadFullImage(message.data.Image!!.link)
      }
      thread.start()
      synchronized(thread) {
         thread.join()
      }
      return image
   }

   private fun loadMessages() {
      Thread {
         messagesDatabase.getAllMessages().forEach {
            messages += it.toMessage()
         }
         startMessageReceiver()
      }.start()
   }

   private fun updateMessages() {
      Thread {
         semaphoreReceive.acquire()
         val newMessages = try {
            receiveMapper.readValue<MutableList<Message>>(
               network.getLastMessages((messages.size + 1).toLong())
            )
         } catch (e: Exception) {
            mutableListOf()
         }
         getImages(newMessages)
         val initialSize = messages.size
         messages += newMessages
         newMessages.forEach {
            messagesDatabase.insertMessage(it.toEntity())
         }
         val updatedSize = messages.size
         val intent = Intent(TAG)
         intent.putExtra("type", NEW_MESSAGES)
         intent.putExtra("initialSize", initialSize)
         intent.putExtra("updatedSize", updatedSize)
         LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
         semaphoreReceive.release()
      }.start()
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
            val responseCode = network.sendImageMessage(file, code)
            if (responseCode != HttpURLConnection.HTTP_OK) {
               sendIntent(SEND_IMAGE_FAILED, "Server error: http code $responseCode")
            }
            updateMessages()
         } catch (e: Exception) {
            sendIntent(SERVER_ERROR)
         } finally {
            file.delete()
         }
      }.start()
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
            @Suppress("DEPRECATION")
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
               val responseCode = network.sendTextMessage(json)
               if (responseCode != 200) {
                  sendIntent(Constants.SEND_MESSAGE_FAILED, responseCode.toString())
               }
               updateMessages()
            } catch (e: Exception) {
               sendIntent(SERVER_ERROR)
            }
         }.start()
      } else {
         sendIntent(ERROR, "Message can't be empty")
      }
   }

   private fun getImages(messages: MutableList<Message>) {
      messages.forEach { message ->
         if (message.data.Image?.link?.isNotEmpty() == true) {
            message.data.Image.bitmap = network.downloadThumbImage(message.data.Image.link)
         }
      }
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

   private fun MessageEntity.toMessage(): Message {
      return if (this.text != null) Message(
         from = this.from,
         to = this.to,
         data = Data(Text = Text(this.text)),
         time = this.time
      ) else Message(
         from = this.from,
         to = this.to,
         data = Data(Image = Image(link = this.link!!)),
         time = this.time
      )
   }

   private fun Message.toEntity(): MessageEntity {
      return if (this.data.Text != null) {
         MessageEntity(
            0,
            this.from,
            this.to,
            this.data.Text.text,
            null,
            this.time
         )
      } else {
         MessageEntity(
            0,
            this.from,
            this.to,
            null,
            this.data.Image!!.link,
            this.time
         )
      }
   }

}