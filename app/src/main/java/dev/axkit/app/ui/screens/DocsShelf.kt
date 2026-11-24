package dev.axkit.app.ui.screens

import dev.axkit.core.DocGraphView
import dev.axkit.core.KnowledgeView
import dev.axkit.core.KoreanSearch
import java.time.Instant

/** Documents as the list shows them: one shape for both sources (`knowledge` and `docgraph` ids differ). */
class DocShelf(val sections: List<Section>) {
    data class Doc(
        val id: String,
        val title: String,
        val summary: String?,
        val updatedAt: Instant?,
        val source: String,
        val section: String,
        /** §21.7: related items' titles (docgraph tree). */
        val related: List<String> = emptyList(),
    ) {
        val key: String get() = "$source:$id"
    }

    data class Section(val id: String, val label: String, val summary: String?, val docs: List<Doc>)

    /** Every document once. */
    val all: List<Doc> = sections.flatMap { it.docs }.distinctBy { it.key }

    /** Newest first. */
    fun recent(limit: Int): List<Doc> = all.filter { it.updatedAt != null }.sortedByDescending { it.updatedAt }.take(limit)

    companion object {
        fun from(k: KnowledgeView): List<Section> = k.areas.map { a ->
            Section("k:" + a.id, a.label, a.summary, a.docs.map { Doc(it.id, it.title, it.summary, it.updatedAt, "knowledge", a.label) })
        }.filter { it.docs.isNotEmpty() }

        fun from(g: DocGraphView): List<Section> {
            val tree = g.tree
            if (!tree.isNullOrEmpty()) {
                return tree.map { layer ->
                    Section("g:" + layer.id, layer.label, layer.summary, layer.items.map {
                        Doc(it.id, it.title, it.summary, it.updatedAt, "docgraph", layer.label, it.related.orEmpty().map { r -> r.title })
                    })
                }.filter { it.docs.isNotEmpty() }
            }
            return g.layers.map { layer ->
                Section("g:" + layer.id, layer.label, layer.summary,
                    g.nodes.filter { it.layer == layer.id }.map { Doc(it.id, it.title, it.summary, it.updatedAt, "docgraph", layer.label) })
            }.filter { it.docs.isNotEmpty() }
        }
    }
}

/** One search result: the document, the line to show under it, and how it was found (0 title … 3 body). */
data class DocHit(val doc: DocShelf.Doc, val snippet: String?, val rank: Int) {
    val key: String get() = doc.key
}

object DocSearch {
    /** The Mac's hits first (§21.6), then what the app finds itself in titles, summaries, 분야 and the bodies it has read. */
    fun findLocal(q: String, docs: List<DocShelf.Doc>, bodies: Map<String, String>, fromMac: List<DocHit>): List<DocHit> {
        val seen = fromMac.map { it.key }.toSet()
        val local = docs.filter { it.key !in seen }.mapNotNull { d ->
            when {
                KoreanSearch.matches(d.title, q) -> DocHit(d, d.summary, 0)
                d.summary != null && KoreanSearch.matches(d.summary, q) -> DocHit(d, d.summary, 1)
                KoreanSearch.matches(d.section, q) -> DocHit(d, null, 2)
                else -> bodies[d.key]?.let { KoreanSearch.snippet(it, q) }?.let { DocHit(d, it, 3) }
            }
        }.sortedWith(compareBy<DocHit> { it.rank }.thenByDescending { it.doc.updatedAt ?: Instant.MIN })
        return fromMac + local
    }
}
