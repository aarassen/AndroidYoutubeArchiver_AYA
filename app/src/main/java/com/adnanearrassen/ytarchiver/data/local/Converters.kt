package com.adnanearrassen.ytarchiver.data.local

import androidx.room.TypeConverter
import com.adnanearrassen.ytarchiver.domain.model.DownloadStatus
import com.adnanearrassen.ytarchiver.domain.model.DownloadType
import com.adnanearrassen.ytarchiver.domain.model.MediaKind

/** Persists enums by name so reordering enum constants never corrupts data. */
class Converters {
    @TypeConverter fun mediaKindToString(v: MediaKind): String = v.name
    @TypeConverter fun stringToMediaKind(v: String): MediaKind = MediaKind.valueOf(v)

    @TypeConverter fun downloadStatusToString(v: DownloadStatus): String = v.name
    @TypeConverter fun stringToDownloadStatus(v: String): DownloadStatus = DownloadStatus.valueOf(v)

    @TypeConverter fun downloadTypeToString(v: DownloadType): String = v.name
    @TypeConverter fun stringToDownloadType(v: String): DownloadType = DownloadType.valueOf(v)
}
