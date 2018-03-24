package net.nullsum.audinaut.service

import net.nullsum.audinaut.util.Util

import android.content.Context


object MusicServiceFactory {
    @JvmStatic
    val onlineService: MusicService = CachedMusicService(RESTMusicService())

    @JvmStatic
    val offlineService: MusicService = OfflineMusicService()

    @JvmStatic
    fun getMusicService(context: Context) : MusicService {
        return if (Util.isOffline(context)) offlineService else onlineService
    }
}
