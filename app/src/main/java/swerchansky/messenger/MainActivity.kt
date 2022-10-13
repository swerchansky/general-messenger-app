package swerchansky.messenger

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import swerchansky.adapters.Adapter
import swerchansky.messenger.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
   lateinit var mainActivity: ActivityMainBinding
   private val mainHandler = Handler(Looper.getMainLooper())

   override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      mainActivity = ActivityMainBinding.inflate(layoutInflater)
      setContentView(mainActivity.root)

      val recycler = mainActivity.messagesRecycler

      recycler.apply {
         layoutManager = LinearLayoutManager(this@MainActivity)
         adapter = Adapter(this@MainActivity, emptyList())
      }

//      Thread {
//         val con: HttpURLConnection = URL("http://213.189.221.170:8008/1ch").openConnection() as HttpURLConnection
//         con.requestMethod = "GET"
//         val response = con.inputStream.bufferedReader().readText()
//         println(response)
//         val mapper = jacksonObjectMapper()
//         mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
//         val messages = mapper.readValue(response, Array<Message>::class.java).toList()
//         println(messages)
//
//         mainHandler.post {
//            mainActivity.messagesRecycler.adapter =
//               Adapter(this, messages)
//         }
//      }.start()
   }
}