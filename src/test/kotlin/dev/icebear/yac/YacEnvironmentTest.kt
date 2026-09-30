package dev.icebear.yac

import com.intellij.openapi.components.service
import dev.icebear.yac.settings.YacSettings

class YacEnvironmentTest : YacTestCase() {
    fun testRootIsTheNearestDirectoryWithYacOrGit() {
        val tree = TemporaryTree.create().file(".git", "gitdir: elsewhere").directory("app/.yac").file("app/src/A.php").file("lib/B.php").file("app/.yac.txt")

        assertEquals(tree.find("app"), YacEnvironment.rootOf(tree.find("app/src/A.php")))
        assertEquals(tree.find("app"), YacEnvironment.rootOf(tree.find("app")))
        assertEquals(tree.find(), YacEnvironment.rootOf(tree.find("lib/B.php")))
        assertNull(YacEnvironment.rootOf(TemporaryTree.create().file("A.php").find("A.php")))
    }

    fun testYacIsTheNearestVendorBinaryUpToTheRootThenTheRepositoryBinary() {
        val tree = TemporaryTree.create().directory("project/.git").file("vendor/bin/yac").file("project/bin/yac")
            .file("project/app/vendor/bin/yac").file("project/app/src/A.php").file("project/lib/B.php")
        val root = tree.find("project")

        assertEquals(tree.find("project/app/vendor/bin/yac"), YacEnvironment.yacOf(tree.find("project/app/src/A.php"), root, ""))
        assertEquals(tree.find("project/bin/yac"), YacEnvironment.yacOf(tree.find("project/lib/B.php"), root, ""))
        assertEquals(tree.find("project/bin/yac"), YacEnvironment.yacOf(root, root, ""))
    }

    fun testConfiguredYacIsRelativeToTheRootOrAbsoluteAndMustBeAFile() {
        val tree = TemporaryTree.create().directory(".git").file("vendor/bin/yac").file("tools/yac").file("A.php")
        val file = tree.find("A.php")
        val root = tree.find()

        assertEquals(tree.find("tools/yac"), YacEnvironment.yacOf(file, root, "tools/yac"))
        assertEquals(tree.find("tools/yac"), YacEnvironment.yacOf(file, root, tree.path.resolve("tools/yac").toString()))
        assertNull(YacEnvironment.yacOf(file, root, "missing/yac"))
        assertNull(YacEnvironment.yacOf(file, root, "tools"))
    }

    fun testPhpSettingWinsOverTheProjectInterpreter() {
        assertEquals("/usr/local/bin/php", YacEnvironment.php("/usr/local/bin/php", "/usr/bin/php"))
        assertEquals("/usr/bin/php", YacEnvironment.php("", "/usr/bin/php"))
        assertEquals("php", YacEnvironment.php("", null))
    }

    fun testSourcesAreRelativeToTheRoot() {
        val tree = TemporaryTree.create().directory("p/.yac").fakeYac("p/vendor/bin/yac").file("p/src/A.php").file("outside.php")
        val environment = YacEnvironment.of(project, tree.find("p/src/A.php"))!!

        assertEquals(tree.find("p"), environment.root)
        assertTrue(environment.isInitialized)
        assertNotNull(environment.cli)
        assertEquals(listOf(".", "src", "src/A.php", null), listOf(tree.find("p"), tree.find("p/src"), tree.find("p/src/A.php"), tree.find("outside.php")).map { environment.sourceOf(it) })
    }

    fun testWithoutBinaryOrYacDirectory() {
        val tree = TemporaryTree.create().directory(".git").file("A.php")
        val environment = YacEnvironment.of(project, tree.find("A.php"))!!

        assertNull(environment.cli)
        assertFalse(environment.isInitialized)
    }

    fun testBlankSettingsAreEmpty() {
        val settings = project.service<YacSettings>()
        settings.state.yacPath = "  "
        settings.state.phpPath = " /usr/bin/php "

        assertEquals("" to "/usr/bin/php", settings.yacPath to settings.phpPath)
    }
}
