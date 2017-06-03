package net.nullsum.audinaut.domain

import android.content.Context
import android.content.SharedPreferences

import kotlin.collections.MutableList
import java.io.Serializable

import net.nullsum.audinaut.util.Constants
import net.nullsum.audinaut.util.Util

class Indexes constructor(var lastModified: Long = 0,
                          var shortcuts: MutableList<Artist> = mutableListOf<Artist>(),
                          var artists: MutableList<Artist> = mutableListOf<Artist>(),
                          var entries: MutableList<MusicDirectory.Entry> = mutableListOf<MusicDirectory.Entry>()) : Serializable {
    fun sortChildren(context: Context) {
    }
}
