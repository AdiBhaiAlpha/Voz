package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ContactAssistEntity::class,
        BlockedNumberEntity::class,
        CallReminderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VozDatabase : RoomDatabase() {
    abstract fun vozAssistDao(): VozAssistDao

    companion object {
        @Volatile
        private var INSTANCE: VozDatabase? = null

        fun getInstance(context: Context): VozDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VozDatabase::class.java,
                    "voz_call_assist.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
