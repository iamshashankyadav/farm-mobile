package com.farm.customer.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestTest {
    private val vocab = listOf("Apple", "Banana", "Tomato", "Tomato, Desi", "Potato", "Pineapple", "Mango", "Green Peas", "Carrot")
    private fun words(q: String) = suggestWords(q, vocab).map { it.text }

    private fun item(id: String, crop: String) = ProduceListing(
        id = id, farmId = "f$id", farmName = "Farm $id", cropId = crop.lowercase(), cropName = crop,
        unit = "kg", pricePerUnit = 1.0, qtyAvailable = 1.0, harvestDate = "",
    )

    @Test fun findsWordsByTheirStartBestFirst() {
        assertEquals("Apple", words("app").first())
        assertEquals(listOf("Tomato", "Tomato, Desi"), words("tom"))
    }

    @Test fun findsAppleFromAplBySkippedLetters() {
        assertTrue(words("apl").contains("Apple"))
        assertEquals("Apple", words("apl").first())
    }

    @Test fun matchesALaterWordAndTheMiddleOfAWord() {
        assertTrue(words("desi").contains("Tomato, Desi"))
        assertTrue(words("ato").containsAll(listOf("Tomato", "Potato")))
    }

    @Test fun forgivesATypo() {
        assertTrue(words("tomatto").contains("Tomato"))
        assertTrue(words("bananna").contains("Banana"))
    }

    @Test fun ignoresCaseAndNeedsTwoLettersAndHidesTheExactWord() {
        assertEquals(listOf("Mango"), words("  MAN"))
        assertTrue(words("a").isEmpty())
        assertTrue(!words("mango").contains("Mango"))
        assertTrue(words("zzzq").isEmpty())
    }

    @Test fun editDistanceCountsSingleEdits() {
        assertEquals(1, editDistance("tomato", "tomatto"))
        assertTrue(editDistance("abc", "xyzxyz") > 2)
    }

    @Test fun narrowsToTheClosestSuggestionWhenNothingContainsTheText() {
        val items = listOf(item("1", "Tomato"), item("2", "Mango"))
        assertEquals(listOf("2"), narrowByQuery(items, "mng").map { it.id })
        // a normal substring still wins, and an empty box shows everything
        assertEquals(listOf("1"), narrowByQuery(items, "toma").map { it.id })
        assertEquals(2, narrowByQuery(items, "").size)
    }
}
