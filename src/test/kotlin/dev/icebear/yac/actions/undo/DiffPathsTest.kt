package dev.icebear.yac.actions.undo

import org.junit.Assert.assertEquals
import org.junit.Test

class DiffPathsTest {
    @Test
    fun listsEveryHeaderPairOnce() {
        val diff = "--- a/src/A.php\n+++ b/src/A.php\n@@ -1 +1 @@\n-old\n+new\n--- a/src/B.php\n+++ b/src/B.php\n@@ -1 +1 @@\n-old\n+new\n--- a/src/A.php\n+++ b/src/A.php\n"

        assertEquals(listOf("src/A.php", "src/B.php"), DiffPaths.of(diff))
    }

    @Test
    fun ignoresABodyLineThatLooksLikeAHeaderWithoutItsPartner() {
        val diff = "--- a/src/A.php\n+++ b/src/A.php\n@@ -1,2 +1,2 @@\n--- a/not/a/header.php\n+changed\n--- a/x.php\n+++ b/y.php\nsummary\n"

        assertEquals(listOf("src/A.php"), DiffPaths.of(diff))
    }

    @Test
    fun emptyOutputHasNoPaths() {
        assertEquals(emptyList<String>(), DiffPaths.of(""))
        assertEquals(emptyList<String>(), DiffPaths.of("Nothing to do.\n"))
        assertEquals(emptyList<String>(), DiffPaths.of("--- a/only/old.php"))
    }

    @Test
    fun handlesWindowsLineEndings() {
        assertEquals(listOf("src/A.php"), DiffPaths.of("--- a/src/A.php\r\n+++ b/src/A.php\r\n"))
    }
}
