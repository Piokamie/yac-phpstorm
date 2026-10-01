package dev.icebear.yac.actions.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextEditTest {
    private fun edit(before: String, after: String): Triple<Int, Int, String>? = TextEdit.between(before, after)?.let { Triple(it.start, it.end, it.replacement) }

    @Test
    fun equalTextsNeedNoEdit() {
        assertNull(TextEdit.between("abc", "abc"))
    }

    @Test
    fun keepsTheCommonPrefixAndSuffix() {
        assertEquals(Triple(2, 2, "X"), edit("abcd", "abXcd"))
        assertEquals(Triple(2, 3, ""), edit("abXcd", "abcd"))
        assertEquals(Triple(0, 3, "xyz"), edit("abc", "xyz"))
    }

    @Test
    fun prefixAndSuffixNeverOverlap() {
        assertEquals(Triple(2, 2, "a"), edit("aa", "aaa"))
        assertEquals(Triple(2, 3, ""), edit("aaa", "aa"))
        assertEquals(Triple(0, 0, "x"), edit("", "x"))
    }
}
