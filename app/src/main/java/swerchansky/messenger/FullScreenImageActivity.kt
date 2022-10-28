package swerchansky.messenger

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import swerchansky.messenger.databinding.ActivityFullScreenImageBinding
import swerchansky.services.MessageService


class FullScreenImageActivity : AppCompatActivity() {
   private lateinit var fullScreenImageBinding: ActivityFullScreenImageBinding
   private lateinit var messageServiceIntent: Intent
   private val scope = CoroutineScope(Dispatchers.IO)
   private var messageService: MessageService? = null
   private var isBound = false
   private val handler = Handler(Looper.getMainLooper())

   private val boundServiceConnection: ServiceConnection = object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName, service: IBinder) {
         val binderBridge: MessageService.MyBinder = service as MessageService.MyBinder
         messageService = binderBridge.getService()
         val position = intent.getIntExtra("messagePosition", -1)
         if (position != -1) {
            scope.launch {
               val image = withContext(Dispatchers.IO) {
                  messageService!!.getFullImage(position)
               }
               withContext(Dispatchers.Main) {
                  fullScreenImageBinding.fullScreenImage.setImageBitmap(image)
               }
            }
         }
         isBound = true
      }

      override fun onServiceDisconnected(name: ComponentName) {
         isBound = false
         messageService = null
      }
   }

   override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      fullScreenImageBinding = ActivityFullScreenImageBinding.inflate(layoutInflater)
      setContentView(fullScreenImageBinding.root)

      messageServiceIntent = Intent(this, MessageService::class.java)
      startService(messageServiceIntent)
      bindService(messageServiceIntent, boundServiceConnection, BIND_AUTO_CREATE)
   }

   override fun onDestroy() {
      super.onDestroy()
      if (isBound) {
         unbindService(boundServiceConnection)
      }
   }
}