package net.nullsum.audinaut.domain

import java.io.Serializable
import java.util.*

class Indexes(var shortcuts: MutableList<Artist> = mutableListOf(),
              var artists: MutableList<Artist> = mutableListOf(),
              var entries: MutableList<MusicDirectory.Entry> = mutableListOf()) : Serializable {

    fun sortChildren() {
        shortcuts.sortBy { s -> s.id.toLowerCase(Locale.ROOT)  }
        artists.sortBy { a -> a.name.toLowerCase(Locale.ROOT) }
        entries.sortBy { e -> e.artist.toLowerCase(Locale.ROOT) }
    }
}
