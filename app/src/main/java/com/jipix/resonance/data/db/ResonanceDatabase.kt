package com.jipix.resonance.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        PlaybackStatEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ResonanceDatabase : RoomDatabase() {

    abstract fun musicDao(): MusicDao

    companion object {
        fun build(context: Context): ResonanceDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ResonanceDatabase::class.java,
                "resonance.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /**
         * Adds the folder and bitrate columns, then empties the song cache.
         *
         * Clearing is deliberate: [MusicRepository.sync] only rewrites rows whose
         * file changed, so migrated rows would keep an empty folder forever and the
         * blacklist would never see them. Playlists and play counts survive.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN folder TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE songs ADD COLUMN bitrate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_folder ON songs(folder)")
                db.execSQL("DELETE FROM songs")
            }
        }

        /** Playlists gain a chosen cover; 0 keeps the automatic mosaic. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE playlists ADD COLUMN coverAlbumId INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
