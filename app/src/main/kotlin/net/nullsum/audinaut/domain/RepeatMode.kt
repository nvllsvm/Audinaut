package net.nullsum.audinaut.domain

enum class RepeatMode {
    OFF {
        override fun next() = ALL
    },
    ALL {
        override fun next() = SINGLE
    },
    SINGLE {
        override fun next() = OFF
    };

    abstract fun next(): RepeatMode
}
