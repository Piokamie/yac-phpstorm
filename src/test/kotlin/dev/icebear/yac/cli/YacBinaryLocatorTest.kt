package dev.icebear.yac.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class YacBinaryLocatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun prefersVendorBinThenTheYacRepositoryBinary() {
        val root = temporaryFolder.root.toPath()
        Files.createDirectories(root.resolve("bin"))
        Files.writeString(root.resolve("bin/yac"), "")

        assertEquals(root.resolve("bin/yac"), YacBinaryLocator(root, "", "", { null }).yac())

        Files.createDirectories(root.resolve("vendor/bin"))
        Files.writeString(root.resolve("vendor/bin/yac"), "")

        assertEquals(root.resolve("vendor/bin/yac"), YacBinaryLocator(root, "", "", { null }).yac())
    }

    @Test
    fun configuredYacPathWinsAndMustExist() {
        val root = temporaryFolder.root.toPath()
        Files.createDirectories(root.resolve("vendor/bin"))
        Files.writeString(root.resolve("vendor/bin/yac"), "")
        Files.writeString(root.resolve("custom-yac"), "")

        assertEquals(root.resolve("custom-yac"), YacBinaryLocator(root, "custom-yac", "", { null }).yac())
        assertNull(YacBinaryLocator(root, "missing-yac", "", { null }).yac())
    }

    @Test
    fun noYacInTheProject() {
        assertNull(YacBinaryLocator(temporaryFolder.root.toPath(), "", "", { null }).yac())
    }

    @Test
    fun phpPrefersAnExistingProjectInterpreterThenTheSettingThenPath() {
        val interpreter = temporaryFolder.newFile("php").toPath().toString()
        val root = temporaryFolder.root.toPath()

        assertEquals(interpreter, YacBinaryLocator(root, "", "/usr/bin/php8", { interpreter }).php())
        assertEquals("/usr/bin/php8", YacBinaryLocator(root, "", "/usr/bin/php8", { "/does/not/exist" }).php())
        assertEquals("/usr/bin/php8", YacBinaryLocator(root, "", "/usr/bin/php8", { null }).php())
        assertEquals("php", YacBinaryLocator(root, "", " ", { null }).php())
    }
}
