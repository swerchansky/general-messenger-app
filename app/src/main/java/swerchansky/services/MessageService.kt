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
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.*
import swerchansky.Constants
import swerchansky.Constants.ERROR
import swerchansky.Constants.FAILED_MESSAGES_SEND_INTERVAL
import swerchansky.Constants.IMAGES_UPDATE_INTERVAL
import swerchansky.Constants.MESSAGES_LOADED
import swerchansky.Constants.MESSAGES_UPDATE_INTERVAL
import swerchansky.Constants.NEW_IMAGE
import swerchansky.Constants.NEW_MESSAGES
import swerchansky.Constants.SEND_IMAGE
import swerchansky.Constants.SEND_IMAGE_FAILED
import swerchansky.Constants.SEND_MESSAGE
import swerchansky.Constants.USERNAME
import swerchansky.db.databases.FailedMessagesDatabase
import swerchansky.db.databases.MessagesDatabase
import swerchansky.db.entities.FailedMessagesEntity
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
import kotlin.math.roundToInt


class MessageService : Service() {
   companion object {
      const val TAG = "MessageService"
      const val MAIN_ACTIVITY_TAG = "MainActivity"
   }

   private val network = NetworkHelper()
   private val scope = CoroutineScope(Dispatchers.IO)
   private val messagesDatabase by lazy {
      MessagesDatabase.getDatabase(this).messagesDAO()
   }
   private val failedMessagesDatabase by lazy {
      FailedMessagesDatabase.getDatabase(this).failedMessagesDAO()
   }
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

   val messages: MutableList<Message> = mutableListOf()

   private var messageReceiverJob = scope.launch(start = CoroutineStart.LAZY) {
      while (isActive) {
         updateMessages()
         delay(MESSAGES_UPDATE_INTERVAL)
      }
   }

   private var messageImageUpdaterJob = scope.launch(start = CoroutineStart.LAZY) {
      while (isActive) {
         updateImages()
         delay(IMAGES_UPDATE_INTERVAL)
      }
   }

   private var failedMessagesSenderJob = scope.launch(start = CoroutineStart.LAZY) {
      while (isActive) {
         sendFailedMessages()
         delay(FAILED_MESSAGES_SEND_INTERVAL)
      }
   }

   private val sendMessageListener: BroadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent) {
         when (intent.getIntExtra("type", -1)) {
            SEND_MESSAGE -> scope.launch {
               prepareAndSendTextMessage(
                  intent.getStringExtra("text") ?: ""
               )
            }
            SEND_IMAGE -> scope.launch {
               prepareAndSendImageMessage(
                  Uri.parse(intent.getStringExtra("uri")) ?: Uri.EMPTY
               )
            }
         }
      }
   }

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
      stopCyclicTasks()
      scope.cancel()
      LocalBroadcastManager.getInstance(this).unregisterReceiver(sendMessageListener)
   }

   inner class MyBinder : Binder() {
      fun getService() = this@MessageService
   }

   private fun loadMessages() {
      scope.launch {
         withContext(Dispatchers.IO) {
            messagesDatabase.getAllMessages().forEach {
               messages += it.toMessage()
            }
            sendIntent(MESSAGES_LOADED)
            startCyclicTasks()
         }
      }
   }

   private suspend fun updateMessages() {
      withContext(Dispatchers.IO) {
         val newMessages = try {
            receiveMapper.readValue<MutableList<Message>>(
               network.getLastMessages((messages.size + 1).toLong())
            )
         } catch (e: Exception) {
            mutableListOf()
         }
         val initialSize = messages.size
         newMessages.forEach {
            val imageId = Date().time
            try {
               messagesDatabase.insertMessage(it.toEntity(imageId))
               if (it.data.Image != null) {
                  it.data.Image.bitmap = compressImage(getImageFromCache(imageId))
               }
               messages += it
            } catch (e: Exception) {
            }
         }
         if (newMessages.isNotEmpty()) {
            val updatedSize = messages.size
            val intent = Intent(TAG)
            intent.putExtra("type", NEW_MESSAGES)
            intent.putExtra("initialSize", initialSize)
            intent.putExtra("updatedSize", updatedSize)
            LocalBroadcastManager.getInstance(this@MessageService).sendBroadcast(intent)
         }
      }
   }

   private fun getImageFromCache(imageId: Long?): Bitmap? {
      return try {
         val file = File(cacheDir, "$imageId.png")
         if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
         } else {
            null
         }
      } catch (e: Exception) {
         null
      }
   }

   private fun compressImage(image: Bitmap?): Bitmap? {
      image ?: return null
      val ratio = image.width.toDouble() / image.height.toDouble()
      return Bitmap.createScaledBitmap(image, 400, (400 / ratio).roundToInt(), false)
   }

   private suspend fun updateImages() {
      withContext(Dispatchers.IO) {
         messages.forEachIndexed { index, it ->
            it.data.Image ?: return@forEachIndexed
            if (it.data.Image.bitmap == null) {
               try {
                  val imageId = messagesDatabase.getMessageItemIdById(it.id!!)
                  writeImageToCache(it, imageId)
                  it.data.Image.bitmap = compressImage(getImageFromCache(imageId))
                  val intent = Intent(TAG)
                  intent.putExtra("type", NEW_IMAGE)
                  intent.putExtra("position", index)
                  LocalBroadcastManager.getInstance(this@MessageService).sendBroadcast(intent)
               } catch (e: Exception) {
                  return@forEachIndexed
               }
            }
         }
      }
   }

   private suspend fun prepareAndSendImageMessage(uri: Uri) {
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
      withContext(Dispatchers.IO) {
         try {
            val responseCode = network.sendImageMessage(file, code)
            if (responseCode != HttpURLConnection.HTTP_OK) {
               sendIntent(SEND_IMAGE_FAILED, "Server error: http code $responseCode")
            }
            scope.launch {
               updateMessages()
            }
         } catch (e: Exception) {
            failedMessagesDatabase.insertFailedMessage(
               FailedMessagesEntity(
                  0,
                  USERNAME,
                  "1@ch",
                  null,
                  uri.toString()
               )
            )
         } finally {
            file.delete()
         }
      }
   }

   fun getFullImage(position: Int): Bitmap? {
      val message = messages[position]
      val imageId = messagesDatabase.getMessageItemIdById(message.id!!)
      return getImageFromCache(imageId)
   }

   private fun getTempFile(image: Bitmap, code: String): File {
      val file = File(this.cacheDir, "${code}temp.png")
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

   private suspend fun prepareAndSendTextMessage(
      text: String,
      from: String = USERNAME,
      to: String = "1@ch"
   ) {
      if (text.isNotEmpty()) {
         val message = Message(
            from,
            to,
            Data(Text = Text(text)),
            Date().time.toString()
         )
         kotlin.runCatching {
            sendMapper.writeValueAsString(message).replaceFirst("text", "Text")
         }.onSuccess { json ->
            withContext(Dispatchers.IO) {
               try {
                  val responseCode = network.sendTextMessage(json)
                  if (responseCode != 200) {
                     sendIntent(Constants.SEND_MESSAGE_FAILED, responseCode.toString())
                  }
                  scope.launch {
                     updateMessages()
                  }
               } catch (e: Exception) {
                  failedMessagesDatabase.insertFailedMessage(message.toFailedEntity(null))
               }
            }
         } // TODO handle error
      } else {
         sendIntent(ERROR, "Message can't be empty")
      }
   }

   private suspend fun sendFailedMessages() {
      withContext(Dispatchers.IO) {
         val failedMessages = failedMessagesDatabase.getAllFailedMessages()
         failedMessages.forEach {
            if (it.imagePath != null) {
               prepareAndSendImageMessage(Uri.parse(it.imagePath))
            } else {
               prepareAndSendTextMessage(it.text!!, it.from, it.to)
            }
            failedMessagesDatabase.deleteFailedMessage(it)
         }
      }
   }

   private fun sendIntent(type: Int, text: String = "") {
      val intent = Intent(TAG)
      intent.putExtra("type", type)
      intent.putExtra("text", text)
      LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
   }

   private fun startCyclicTasks() {
      messageReceiverJob.start()
      messageImageUpdaterJob.start()
      failedMessagesSenderJob.start()
   }

   private fun stopCyclicTasks() {
      messageReceiverJob.cancel()
      messageImageUpdaterJob.cancel()
      failedMessagesSenderJob.cancel()
   }

   private fun writeImageToCache(message: Message, imageId: Long) {
      val image = network.downloadFullImage(message.data.Image!!.link)
      image ?: return
      val file =
         File(this@MessageService.cacheDir, "$imageId.png").also { it.createNewFile() }
      val bos = ByteArrayOutputStream()
      image.compress(Bitmap.CompressFormat.PNG, 0, bos)
      val bitmapData = bos.toByteArray()
      FileOutputStream(file).use {
         with(it) {
            write(bitmapData)
            flush()
         }
      }
   }

   private fun Message.toFailedEntity(path: String?): FailedMessagesEntity {
      return FailedMessagesEntity(
         0,
         this.from,
         this.to,
         this.data.Text?.text,
         path
      )
   }

   private fun MessageEntity.toMessage(): Message {
      return if (this.text != null) {
         Message(
            id = this.id,
            from = this.from,
            to = this.to,
            data = Data(Text = Text(this.text)),
            time = this.time
         )
      } else {
         Message(
            id = this.id,
            from = this.from,
            to = this.to,
            data = Data(
               Image = Image(
                  link = this.link!!,
                  bitmap = compressImage(
                     getImageFromCache(this.imageId)
                  )
               )
            ),
            time = this.time
         )
      }
   }

   private fun Message.toEntity(imageId: Long): MessageEntity {
      if (this.data.Text != null) {
         return MessageEntity(
            this.id!!,
            null,
            this.from,
            this.to,
            this.data.Text.text,
            null,
            this.time
         )
      } else {
         val entity = MessageEntity(
            this.id!!,
            imageId,
            this.from,
            this.to,
            null,
            this.data.Image!!.link,
            this.time
         )
         return try {
            writeImageToCache(this, imageId)
            entity
         } catch (e: Exception) {
            entity
         }
      }
   }
}
