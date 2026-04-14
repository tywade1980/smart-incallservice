package com.aireceptionist.app.di

import android.content.Context
import androidx.room.Room
import com.aireceptionist.app.data.dao.*
import com.aireceptionist.app.data.database.AIDatabase
import com.aireceptionist.app.data.repository.AppointmentRepository
import com.aireceptionist.app.data.repository.CallRepository
import com.aireceptionist.app.data.repository.KnowledgeBaseRepository
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
    fun provideDatabase(@ApplicationContext context: Context): AIDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            AIDatabase::class.java,
            AIDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideCallDao(db: AIDatabase): CallDao = db.callDao()

    @Provides
    fun provideAppointmentDao(db: AIDatabase): AppointmentDao = db.appointmentDao()

    @Provides
    fun provideCallerHistoryDao(db: AIDatabase): CallerHistoryDao = db.callerHistoryDao()

    @Provides
    fun provideFAQDao(db: AIDatabase): FAQDao = db.faqDao()

    @Provides
    fun provideKnowledgeBaseDao(db: AIDatabase): KnowledgeBaseDao = db.knowledgeBaseDao()

    @Provides
    fun provideTrainingDataDao(db: AIDatabase): TrainingDataDao = db.trainingDataDao()

    @Provides
    fun provideAgentMetricsDao(db: AIDatabase): AgentMetricsDao = db.agentMetricsDao()

    @Provides
    @Singleton
    fun provideCallRepository(callDao: CallDao): CallRepository = CallRepository(callDao)

    @Provides
    @Singleton
    fun provideAppointmentRepository(appointmentDao: AppointmentDao): AppointmentRepository =
        AppointmentRepository(appointmentDao)

    @Provides
    @Singleton
    fun provideKnowledgeBaseRepository(
        knowledgeBaseDao: KnowledgeBaseDao,
        faqDao: FAQDao
    ): KnowledgeBaseRepository = KnowledgeBaseRepository(knowledgeBaseDao, faqDao)
}
