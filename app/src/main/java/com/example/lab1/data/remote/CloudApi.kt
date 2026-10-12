package com.example.lab1.data.remote

import com.example.lab1.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

private const val DEVICE_ID = "deviceId"
private const val READINGS_PATH = "${CloudSchema.DEVICES}/{$DEVICE_ID}/${CloudSchema.READINGS}.json"
private const val LATEST_PATH = "${CloudSchema.DEVICES}/{$DEVICE_ID}/${CloudSchema.LATEST}.json"

/**
 * Firebase Realtime Database REST API: any database node is available as <path>.json.
 * The ns parameter is needed only by the local Firebase emulator; null is not added to the request.
 */
interface CloudApi {

    /** POST creates a new record with a unique push id — this is how history accumulates. */
    @POST(READINGS_PATH)
    suspend fun postReading(
        @Path(DEVICE_ID) deviceId: String,
        @Body data: SensorData,
        @Query(CloudSchema.NAMESPACE_PARAM) namespace: String? = null
    ): PushResponse

    /** PUT overwrites the node — it always holds the latest reading. */
    @PUT(LATEST_PATH)
    suspend fun putLatest(
        @Path(DEVICE_ID) deviceId: String,
        @Body data: SensorData,
        @Query(CloudSchema.NAMESPACE_PARAM) namespace: String? = null
    ): SensorData

    companion object {
        fun create(baseUrl: String): CloudApi {
            val logging = HttpLoggingInterceptor().apply {
                // Request bodies are logged only in the debug build
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                else HttpLoggingInterceptor.Level.NONE
            }
            val client = OkHttpClient.Builder().addInterceptor(logging).build()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(CloudApi::class.java)
        }
    }
}
