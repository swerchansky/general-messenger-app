package swerchansky.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import swerchansky.Constants.URL
import swerchansky.Constants.USERNAME
import swerchansky.messenger.Message
import java.io.File


class NetworkHelper {
   private val mapper = JsonMapper
      .builder()
      .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
      .serializationInclusion(JsonInclude.Include.NON_NULL)
      .build()
      .registerModule(KotlinModule.Builder().build())
   private val retrofitReceiver = Retrofit.Builder()
      .baseUrl(URL)
      .addConverterFactory(ScalarsConverterFactory.create())
      .addConverterFactory(JacksonConverterFactory.create(mapper))
      .build()
      .create(APIService::class.java)

   fun getLastMessages(from: Long, count: Long = 100): List<Message>? {
      val response = retrofitReceiver.getLastMessages(from, count).execute()
      return response.body()  // TODO handle errors
   }

   fun downloadFullImage(link: String): Bitmap? {
      val body = retrofitReceiver.getFullImage(link).execute().body()
      body ?: return null
      val bytes = body.bytes()
      return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
   }

   fun sendTextMessage(json: String): Int {
      val response = retrofitReceiver.sendTextMessage(json).execute()
      return response.code()
   }

   fun sendImageMessage(file: File, code: String): Int {
      val requestBody = RequestBody.create(MediaType.parse("multipart/form-data"), file)
      val imageFileBody = MultipartBody.Part.createFormData("picture", file.name, requestBody)

      val json: RequestBody = RequestBody.create(
         MediaType.parse("application/json"),
         "{\"from\":\"$USERNAME\"}"
      )

      val response = retrofitReceiver.sendImageMessage(json, imageFileBody).execute()
      return response.code()
   }
}