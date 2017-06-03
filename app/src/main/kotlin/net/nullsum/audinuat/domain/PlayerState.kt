package net.nullsum.audinaut.domain

import android.media.RemoteControlClient

enum class PlayerState constructor(val mRemoteControlClientPlayState: Int) {
    IDLE(RemoteControlClient.PLAYSTATE_STOPPED),
    DOWNLOADING(RemoteControlClient.PLAYSTATE_BUFFERING),
    PREPARING(RemoteControlClient.PLAYSTATE_BUFFERING),
    PREPARED(RemoteControlClient.PLAYSTATE_STOPPED),
    STARTED(RemoteControlClient.PLAYSTATE_PLAYING),
    STOPPED(RemoteControlClient.PLAYSTATE_STOPPED),
    PAUSED(RemoteControlClient.PLAYSTATE_PAUSED),
    PAUSED_TEMP(RemoteControlClient.PLAYSTATE_PAUSED),
    COMPLETED(RemoteControlClient.PLAYSTATE_STOPPED);

    fun getRemoteControlClientPlayState(): Int {
        return mRemoteControlClientPlayState
    }
}
