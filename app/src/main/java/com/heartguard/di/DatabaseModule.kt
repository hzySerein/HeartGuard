package com.heartguard.di

import android.content.Context
import com.heartguard.data.local.AppDao
import com.heartguard.data.local.AppDatabase
import com.heartguard.data.local.ChatDao
import com.heartguard.data.local.FraudDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideAppDao(database: AppDatabase): AppDao {
        return database.appDao()
    }

    @Provides
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    fun provideFraudDao(database: AppDatabase): FraudDao {
        return database.fraudDao()
    }
}
