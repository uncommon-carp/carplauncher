package app.olauncher.focus

import java.text.Normalizer
import java.util.Locale

private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
private val separators = Regex("[-_+,.`'\\s\\p{Z}]+")

private fun String.folded(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(diacritics, "").lowercase(Locale.ROOT)

/** Match tiers, best first. Within a tier, launch frequency decides. */
object MatchTier {
    const val EXACT = 6
    const val PREFIX = 5
    const val WORD_PREFIX = 4
    const val SUBSTRING = 3
    const val INITIALS = 2
    const val SUBSEQUENCE = 1
}

/** Minimum query length for loose in-order character matching, to keep short queries precise. */
const val SUBSEQUENCE_MIN_QUERY = 3

/**
 * Fuzzy match of an app label against a drawer query. Returns a [MatchTier] or null for no match.
 * Anything upstream's [app.olauncher.helper.appLabelMatches] accepts scores at least [MatchTier.SUBSTRING].
 */
fun fuzzyMatchTier(label: String, query: CharSequence): Int? {
    val q = query.toString().trim().folded()
    if (q.isEmpty() || label.isBlank()) return null
    val l = label.folded()
    val words = l.split(separators).filter { it.isNotEmpty() }
    val compactLabel = words.joinToString("")
    val compactQuery = q.replace(separators, "")
    if (compactQuery.isEmpty()) return null

    return when {
        compactLabel == compactQuery -> MatchTier.EXACT
        l.startsWith(q) || compactLabel.startsWith(compactQuery) -> MatchTier.PREFIX
        words.any { it.startsWith(compactQuery) } -> MatchTier.WORD_PREFIX
        l.contains(q) || compactLabel.contains(compactQuery) -> MatchTier.SUBSTRING
        matchesWordPrefixes(words, compactQuery) -> MatchTier.INITIALS
        compactQuery.length >= SUBSEQUENCE_MIN_QUERY && isSubsequence(compactQuery, compactLabel) -> MatchTier.SUBSEQUENCE
        else -> null
    }
}

/**
 * True if [query] can be split into non-empty prefixes of words taken in order (words may be skipped),
 * using at least two words: "gm" and "gmaps" match "google maps".
 */
private fun matchesWordPrefixes(words: List<String>, query: String): Boolean {
    fun go(wordIndex: Int, queryIndex: Int, used: Int): Boolean {
        if (queryIndex == query.length) return used >= 2
        for (w in wordIndex until words.size) {
            val word = words[w]
            var n = 0
            while (n < word.length && queryIndex + n < query.length && word[n] == query[queryIndex + n]) {
                n++
                if (go(w + 1, queryIndex + n, used + 1)) return true
            }
        }
        return false
    }
    return words.size >= 2 && go(0, 0, 0)
}

private fun isSubsequence(needle: String, haystack: String): Boolean {
    var i = 0
    for (c in haystack) if (i < needle.length && c == needle[i]) i++
    return i == needle.length
}

/**
 * Filters and orders drawer items for a query: best tier first, then most launched, then the
 * incoming (alphabetical) order. Blank query: just usage order.
 */
fun <T> searchAndRank(
    items: List<T>,
    query: CharSequence?,
    label: (T) -> String,
    launches: (T) -> Int,
): List<T> {
    if (query.isNullOrBlank()) return sortByUsage(items, launches)
    return items.withIndex()
        .mapNotNull { (i, item) -> fuzzyMatchTier(label(item), query)?.let { Triple(item, it, i) } }
        .sortedWith(
            compareByDescending<Triple<T, Int, Int>> { it.second }
                .thenByDescending { launches(it.first) }
                .thenBy { it.third }
        )
        .map { it.first }
}

/** Stable sort by launch count, descending; equal counts keep their incoming order. */
fun <T> sortByUsage(items: List<T>, launches: (T) -> Int): List<T> =
    items.sortedByDescending(launches)

/** Scrubber bucket for a label: its first letter upper-cased, or '#' for digits and symbols. */
fun scrubberLetter(label: String): Char {
    val first = label.trim().folded().firstOrNull() ?: return '#'
    return if (first.isLetter()) first.uppercaseChar() else '#'
}

/** Letters to show on the scrubber: only ones that have apps, '#' last. */
fun scrubberLetters(labels: List<String>): List<Char> {
    val present = labels.filter { it.isNotBlank() }.map(::scrubberLetter).toSortedSet()
    return present.filter { it != '#' } + listOfNotNull('#'.takeIf { it in present })
}
