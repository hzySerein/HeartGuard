package com.heartguard.di

import android.content.Context
import com.heartguard.data.remote.AiGateway
import com.heartguard.data.remote.VivoAiRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides
    @Singleton
    fun provideAiGateway(@ApplicationContext context: Context): AiGateway {
        return VivoAiRepository(context)
    }
}
