package com.wefit.app.di

import android.content.Context
import androidx.room.Room
import com.wefit.app.data.local.WeFitDatabase
import com.wefit.app.data.local.WeFitMigrations
import com.wefit.app.data.preferences.PreferencesRepositoryImpl
import com.wefit.app.data.repository.AchievementRepositoryImpl
import com.wefit.app.data.repository.ActivityRepositoryImpl
import com.wefit.app.data.repository.GoalRepositoryImpl
import com.wefit.app.data.repository.ProfileRepositoryImpl
import com.wefit.app.data.repository.ReminderRepositoryImpl
import com.wefit.app.data.repository.StepRepositoryImpl
import com.wefit.app.data.repository.WeightRepositoryImpl
import com.wefit.app.domain.repository.AchievementRepository
import com.wefit.app.domain.repository.ActivityRepository
import com.wefit.app.domain.repository.GoalRepository
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.domain.repository.ProfileRepository
import com.wefit.app.domain.repository.ReminderRepository
import com.wefit.app.domain.repository.StepRepository
import com.wefit.app.domain.repository.WeightRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WeFitDatabase =
        Room.databaseBuilder(context, WeFitDatabase::class.java, WeFitDatabase.NAME)
            .addMigrations(*WeFitMigrations.ALL)
            .build()

    @Provides fun profileDao(db: WeFitDatabase) = db.profileDao()
    @Provides fun weightDao(db: WeFitDatabase) = db.weightDao()
    @Provides fun stepDao(db: WeFitDatabase) = db.stepDao()
    @Provides fun activityDao(db: WeFitDatabase) = db.activityDao()
    @Provides fun goalDao(db: WeFitDatabase) = db.goalDao()
    @Provides fun reminderDao(db: WeFitDatabase) = db.reminderDao()
    @Provides fun achievementDao(db: WeFitDatabase) = db.achievementDao()
    @Provides fun maintenanceDao(db: WeFitDatabase) = db.maintenanceDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun preferences(impl: PreferencesRepositoryImpl): PreferencesRepository
    @Binds abstract fun profile(impl: ProfileRepositoryImpl): ProfileRepository
    @Binds abstract fun weight(impl: WeightRepositoryImpl): WeightRepository
    @Binds abstract fun steps(impl: StepRepositoryImpl): StepRepository
    @Binds abstract fun activities(impl: ActivityRepositoryImpl): ActivityRepository
    @Binds abstract fun goals(impl: GoalRepositoryImpl): GoalRepository
    @Binds abstract fun reminders(impl: ReminderRepositoryImpl): ReminderRepository
    @Binds abstract fun achievements(impl: AchievementRepositoryImpl): AchievementRepository
}
