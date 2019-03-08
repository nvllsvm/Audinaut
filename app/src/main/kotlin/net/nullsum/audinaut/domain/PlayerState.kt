package net.nullsum.audinaut.domain

enum class PlayerState {
    IDLE,
    DOWNLOADING,
    PREPARING,
    PREPARED,
    STARTED,
    STOPPED,
    PAUSED,
    PAUSED_TEMP,
    COMPLETED;

}
