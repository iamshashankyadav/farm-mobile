package com.farm.customer.browse

import java.text.Normalizer

/**
 * "Did you mean" suggestions for the produce search, like a search engine's autocomplete.
 *
 * Every candidate word gets a score from the first rule that fits, best first:
 *  1. starts with what you typed            "app"  -> apple         (100)
 *  2. a later word starts with it           "des"  -> Tomato, Desi  (80)
 *  3. contains it                           "ato"  -> potato        (60)
 *  4. letters appear in order, with gaps    "apl"  -> apple         (40)
 *  5. a typo away (edit distance 1 or 2)    "tomatto" -> tomato     (20)
 * Shorter words win ties (closer to what was typed), then alphabetical order.
 * The web shop uses the same rules, so both apps suggest the same words.
 */
data class Suggestion(val text: String, val score: Int)

private val ACCENTS = Regex("[\\u0300-\\u036f]")
private val NOT_WORD = Regex("[^\\p{L}\\p{M}\\p{N} ]+")
private val SPACES = Regex("\\s+")

/** Lower case, no accents or punctuation, single spaces: "  Tomató,  Desi " -> "tomato desi". */
fun normalizeText(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(ACCENTS, "").replace(NOT_WORD, " ").replace(SPACES, " ").trim()

/** True when every letter of [q] appears in [word] in the same order ("apl" in "apple"). */
fun isSubsequence(q: String, word: String): Boolean {
    if (q.isEmpty()) return true
    var i = 0
    for (ch in word) {
        if (ch == q[i]) {
            i++
            if (i == q.length) return true
        }
    }
    return false
}

/** Levenshtein distance (single-letter inserts, deletes and swaps); returns max + 1 once it is certainly above [max]. */
fun editDistance(a: String, b: String, max: Int = 2): Int {
    if (kotlin.math.abs(a.length - b.length) > max) return max + 1
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        cur[0] = i
        var best = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            if (cur[j] < best) best = cur[j]
        }
        if (best > max) return max + 1
        prev = cur
    }
    return prev[b.length]
}

private fun scoreOf(q: String, candidate: String): Int {
    val text = normalizeText(candidate)
    if (text.isEmpty()) return 0
    if (text.startsWith(q)) return 100
    val words = text.split(' ')
    if (words.any { it.startsWith(q) }) return 80
    if (text.contains(q)) return 60
    if (q.length >= 3 && words.any { isSubsequence(q, it) }) return 40
    if (q.length >= 4) {
        val allowed = if (q.length >= 7) 2 else 1
        for (w in words) {
            if (editDistance(q, w.take(q.length + 1), allowed) <= allowed) return 20
            if (editDistance(q, w, allowed) <= allowed) return 20
        }
    }
    return 0
}

/** Best matches for [input] from [vocabulary], without duplicates or the exact word typed, at most [limit]. */
fun suggestWords(input: String, vocabulary: Collection<String>, limit: Int = 6): List<Suggestion> {
    val q = normalizeText(input)
    if (q.length < 2) return emptyList()
    val seen = HashSet<String>()
    val out = ArrayList<Suggestion>()
    for (candidate in vocabulary) {
        val key = normalizeText(candidate)
        if (key.isEmpty() || key == q || !seen.add(key)) continue
        val score = scoreOf(q, candidate)
        if (score > 0) out.add(Suggestion(candidate, score))
    }
    return out
        .sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.text.length }.thenBy { it.text })
        .take(limit)
}

/** The words worth suggesting: crop names, varieties, farms and places of the loaded listings. */
fun produceVocabulary(items: List<ProduceListing>, cropNames: List<String> = emptyList()): List<String> {
    val words = LinkedHashSet<String>()
    cropNames.forEach { words.add(it) }
    for (item in items) {
        words.add(item.cropName)
        item.variety?.let { v -> words.add(v) }
        words.add(item.farmName)
        words.add(item.district)
        item.village?.let { v -> words.add(v) }
    }
    return words.filter { it.isNotBlank() }
}

/**
 * The text box narrows the list by what it contains; when nothing contains the typed letters
 * ("mng"), it shows the listings the closest suggestions point at (Mango).
 */
fun narrowByQuery(items: List<ProduceListing>, query: String): List<ProduceListing> {
    val direct = items.filter { matchesQuery(it, query) }
    if (direct.isNotEmpty() || query.isBlank()) return direct
    val near = suggestWords(query, produceVocabulary(items)).map { normalizeText(it.text) }.toSet()
    if (near.isEmpty()) return direct
    return items.filter { item ->
        listOfNotNull(item.cropName, item.variety, item.farmName, item.district, item.village).any { normalizeText(it) in near }
    }
}
