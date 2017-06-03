package net.nullsum.audinaut.domain

import android.util.Log

import java.io.Serializable
import java.text.Collator
import java.util.Collections
import java.util.Comparator
import java.util.Locale

class Artist constructor(var id: String = "", var name: String = "") : Serializable {
    val TAG: String = "Artist"
    val ROOT_ID: String = "-1"
    val MISSING_ID: String = "-2"

    var index: String = ""
    var closeness: Int = 0

    fun sort(artists: MutableList<Artist>, ignoredArticles: MutableList<String>) {
    }
}
