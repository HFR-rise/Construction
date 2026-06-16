package com.example.myapplication.di

import android.content.Context
import com.example.myapplication.data.database.*
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.repository.ContactRepository
import com.example.myapplication.data.repository.ObjectRepository
import com.example.myapplication.data.repository.ProjectRepository
import com.example.myapplication.network.ApiService
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.UserPreferences
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
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    @Singleton
    fun provideMaterialDao(db: AppDatabase): MaterialDao = db.materialDao()

    @Provides
    @Singleton
    fun provideWorkItemDao(db: AppDatabase): WorkItemDao = db.workItemDao()

    @Provides
    @Singleton
    fun provideContactDao(db: AppDatabase): ContactDao = db.contactDao()

    @Provides
    @Singleton
    fun provideContactMethodDao(db: AppDatabase): ContactMethodDao = db.contactMethodDao()

//    @Provides
//    @Singleton
//    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    @Singleton
    fun provideObjectDao(db: AppDatabase): ObjectDao = db.objectDao()

//    @Provides
//    @Singleton
//    fun provideObjectProjectDao(db: AppDatabase): ObjectProjectDao = db.objectProjectDao()

    @Provides
    @Singleton
    fun provideProjectRepository(
        projectDao: ProjectDao,
        materialDao: MaterialDao,
        workItemDao: WorkItemDao,
        contactDao: ContactDao,
        contactMethodDao: ContactMethodDao,
        apiService: ApiService
//        transactionDao: TransactionDao
    ): ProjectRepository = ProjectRepository(
        projectDao,
        materialDao,
        workItemDao,
        contactDao,
        contactMethodDao,
        apiService
//        transactionDao
    )

    @Provides
    @Singleton
    fun provideSyncOperationDao(db: AppDatabase): SyncOperationDao = db.syncOperationDao()

    @Provides
    @Singleton
    fun provideContactRepository(
        contactDao: ContactDao,
        contactMethodDao: ContactMethodDao,
        apiService: ApiService,
        syncManager: SyncManager,
        userPreferences: UserPreferences
    ): ContactRepository = ContactRepository(
        contactDao,
        contactMethodDao,
        apiService,
        syncManager,
        userPreferences
    )

    @Provides
    @Singleton
    fun provideObjectRepository(
        objectDao: ObjectDao,
//        objectProjectDao: ObjectProjectDao,
        projectDao: ProjectDao,
        userPreferences: UserPreferences
    ): ObjectRepository = ObjectRepository(
        objectDao,
//        objectProjectDao,
        projectDao,
        userPreferences
    )
}
