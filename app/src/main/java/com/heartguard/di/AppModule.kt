package com.heartguard.di

import android.content.Context
import com.heartguard.utils.AudioEngine
import com.heartguard.utils.NativeOCRHelper
import com.heartguard.utils.SettingsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideSettingsManager(@ApplicationContext context: Context): SettingsManager {
        return SettingsManager(context)
    }

    @Provides
    @Singleton
    fun provideNativeOCRHelper(@ApplicationContext context: Context): NativeOCRHelper {
        return NativeOCRHelper(context)
    }
}
