package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CompressionHistoryEntity::class], version = 1, exportSchema = false)
abstract class PakForgeDatabase : RoomDatabase() {
    abstract fun historyDao(): CompressionHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: PakForgeDatabase? = null

        fun getInstance(context: Context): PakForgeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PakForgeDatabase::class.java,
                    "pakforge_audit.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
