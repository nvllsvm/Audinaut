package net.nullsum.audinaut.service

import android.content.Context
import net.nullsum.audinaut.util.Util


object MusicServiceFactory {
    @JvmStatic
    val onlineService: MusicService = CachedMusicService(RESTMusicService())

    @JvmStatic
    private val offlineService: MusicService = OfflineMusicService()

    @JvmStatic
    fun getMusicService(context: Context): MusicService {
        return if (Util.isOffline(context)) offlineService else onlineService
    }
}
