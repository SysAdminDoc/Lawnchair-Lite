package app.lawnchairlite.data

object SearchScorer {
    fun score(label: String, packageName: String, query: String): Int {
        val q = query.lowercase()
        val l = label.lowercase()
        val p = packageName.lowercase()
        return when {
            l == q -> 100
            l.startsWith(q) -> 90
            l.split(" ").any { it.startsWith(q) } -> 80
            l.contains(q) -> 70
            p.contains(q) -> 60
            isSubsequence(q, l) -> 50
            else -> 0
        }
    }

    private fun isSubsequence(query: String, text: String): Boolean {
        var queryIndex = 0
        for (ch in text) {
            if (queryIndex < query.length && ch == query[queryIndex]) queryIndex++
        }
        return queryIndex == query.length
    }
}
