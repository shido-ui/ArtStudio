package com.artstudio.manual

import com.artstudio.document.io.ArtJson
import java.io.InputStream

data class ManualSection(
    val id: String,
    val title: String,
    val body: String,
    val visualAidRef: String? = null
)

data class ManualChapter(
    val id: String,
    val title: String,
    val intro: String,
    val sections: List<ManualSection>
)

data class ManualBook(
    val version: String,
    val appName: String,
    val chapters: List<ManualChapter>
) {
    fun search(query: String): List<ManualChapter> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return chapters

        return chapters.mapNotNull { chapter ->
            val titleMatches = chapter.title.lowercase().contains(q)
            val introMatches = chapter.intro.lowercase().contains(q)
            val matchedSections = chapter.sections.filter { sec ->
                sec.title.lowercase().contains(q) || sec.body.lowercase().contains(q)
            }

            if (titleMatches || introMatches || matchedSections.isNotEmpty()) {
                chapter.copy(sections = if (matchedSections.isNotEmpty()) matchedSections else chapter.sections)
            } else {
                null
            }
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun loadFromStream(inputStream: InputStream): ManualBook {
            val text = inputStream.reader(Charsets.UTF_8).readText()
            val map = ArtJson.parse(text) as? Map<String, Any?>
                ?: throw IllegalArgumentException("Invalid manual.json root object")

            val version = map["manual_version"]?.toString() ?: "1.0"
            val appName = map["app_name"]?.toString() ?: "Art Studio"
            val rawChapters = (map["chapters"] as? List<Map<String, Any?>>) ?: emptyList()

            val chapters = rawChapters.map { cMap ->
                val id = cMap["id"]?.toString() ?: ""
                val title = cMap["title"]?.toString() ?: ""
                val intro = cMap["intro"]?.toString() ?: ""
                val rawSecs = (cMap["sections"] as? List<Map<String, Any?>>) ?: emptyList()

                val sections = rawSecs.map { sMap ->
                    ManualSection(
                        id = sMap["id"]?.toString() ?: "",
                        title = sMap["title"]?.toString() ?: "",
                        body = sMap["body"]?.toString() ?: "",
                        visualAidRef = sMap["visual_aid_ref"]?.toString()
                    )
                }

                ManualChapter(id = id, title = title, intro = intro, sections = sections)
            }

            return ManualBook(version, appName, chapters)
        }
    }
}
