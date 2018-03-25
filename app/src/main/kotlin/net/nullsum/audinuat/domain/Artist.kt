package net.nullsum.audinaut.domain

import java.io.Serializable

class Artist constructor(var id: String = "", var name: String = "") : Serializable {
    val TAG: String = "Artist"

    var index: String = ""
    var closeness: Int = 0

    fun sort(artists: MutableList<Artist>, ignoredArticles: MutableList<String>) {
    }
}
