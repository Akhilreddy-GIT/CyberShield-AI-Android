package com.cybershield.ai.di

import android.content.Context
import androidx.room.Room
import com.cybershield.ai.BuildConfig
import com.cybershield.ai.data.local.db.CyberShieldDatabase
import com.cybershield.ai.data.remote.AuthInterceptor
import com.cybershield.ai.data.remote.CyberShieldApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttp(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): CyberShieldApi =
        retrofit.create(CyberShieldApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CyberShieldDatabase =
        Room.databaseBuilder(context, CyberShieldDatabase::class.java, "cybershield.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCaseCacheDao(db: CyberShieldDatabase) = db.caseCacheDao()

    @Provides
    fun provideMessageCacheDao(db: CyberShieldDatabase) = db.messageCacheDao()

    @Provides
    fun provideEvidenceCacheDao(db: CyberShieldDatabase) = db.evidenceCacheDao()
}
