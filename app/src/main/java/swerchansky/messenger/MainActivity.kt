package swerchansky.messenger

import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import swerchansky.Constants.NEW_MESSAGES
import swerchansky.Constants.SEND_MESSAGE
import swerchansky.messenger.databinding.ActivityMainBinding
import swerchansky.recyclers.adapters.MessageAdapter
import swerchansky.services.MessageService


class MainActivity : AppCompatActivity() {
   private lateinit var mainActivity: ActivityMainBinding
   private lateinit var recycler: RecyclerView
   private lateinit var messageServiceIntent: Intent
   private val messages: MutableList<Message> = mutableListOf()
   private val mainHandler = Handler(Looper.getMainLooper())
   private var messageService: MessageService? = null
   private var isBound = false

   private val boundServiceConnection: ServiceConnection = object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName, service: IBinder) {
         val binderBridge: MessageService.MyBinder = service as MessageService.MyBinder
         messageService = binderBridge.getService()
         isBound = true
      }

      override fun onServiceDisconnected(name: ComponentName) {
         isBound = false
         messageService = null
      }
   }

   private val messageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent) {
         when (intent.getIntExtra("type", -1)) {
            NEW_MESSAGES -> updateMessages()
         }
      }
   }

   override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      mainActivity = ActivityMainBinding.inflate(layoutInflater)
      setContentView(mainActivity.root)

      initRecycler()
      initSendListener()

      messageServiceIntent = Intent(this, MessageService::class.java)
      startService(messageServiceIntent)
      bindService(messageServiceIntent, boundServiceConnection, BIND_AUTO_CREATE)
   }

   override fun onStart() {
      super.onStart()
      LocalBroadcastManager.getInstance(this)
         .registerReceiver(messageReceiver, IntentFilter("messageService"))
   }

   override fun onPause() {
      super.onPause()
      LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver)
   }

   override fun onDestroy() {
      super.onDestroy()
      if (isBound) {
         unbindService(boundServiceConnection)
      }
   }

   private fun initSendListener() {
      mainActivity.sendButton.setOnClickListener {
         sendTextMessage(mainActivity.messageField.text.toString())
      }
   }

   private fun initRecycler() {
      recycler = mainActivity.messagesRecycler

      val manager = LinearLayoutManager(this)

      recycler.apply {
         layoutManager = manager
         adapter = MessageAdapter(messages)
      }
   }

   private fun sendTextMessage(text: String) {
      val intent = Intent("mainActivity")
      intent.putExtra("type", SEND_MESSAGE)
      intent.putExtra("text", text)
      LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
      mainActivity.messageField.setText("")
   }

   private fun updateMessages() {
      mainHandler.post {
         val initialSize = recycler.adapter!!.itemCount
         val updatedSize = messageService!!.messages.size
         messages.addAll(
            messageService!!.messages.subList(
               initialSize,
               messageService!!.messages.size
            )
         )
         recycler.post {
            recycler.adapter?.notifyItemRangeInserted(initialSize, updatedSize)
         }
      }
   }
}
