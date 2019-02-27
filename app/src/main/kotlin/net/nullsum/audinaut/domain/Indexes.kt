package net.nullsum.audinaut.domain

import android.content.Context
import java.io.Serializable

class Indexes(var shortcuts: MutableList<Artist> = mutableListOf(),
              var artists: MutableList<Artist> = mutableListOf(),
              var entries: MutableList<MusicDirectory.Entry> = mutableListOf()) : Serializable {
    fun sortChildren(context: Context) {
    }
}
