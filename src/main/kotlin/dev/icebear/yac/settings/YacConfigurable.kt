package dev.icebear.yac.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.panel
import dev.icebear.yac.notes.YacNotesService

class YacConfigurable(private val project: Project) : BoundConfigurable(DISPLAY_NAME) {
    override fun createPanel(): DialogPanel {
        val options = project.service<YacSettings>().state

        return panel {
            row("yac binary:") {
                textField()
                    .columns(COLUMNS_LARGE)
                    .bindText({ options.yacPath.orEmpty() }, { options.yacPath = it.trim() })
                    .comment("Relative to the project root. Empty: vendor/bin/yac, then bin/yac.")
            }
            row("PHP executable:") {
                textField()
                    .columns(COLUMNS_LARGE)
                    .bindText({ options.phpPath.orEmpty() }, { options.phpPath = it.trim() })
                    .comment("Used when the project has no local PHP interpreter. Empty: php from PATH.")
            }
            row {
                checkBox("Show note text above the code").bindSelected({ options.showInlays }, { options.showInlays = it })
            }
        }
    }

    override fun apply() {
        super.apply()
        project.service<YacNotesService>().refreshOpenEditors()
    }

    private companion object {
        const val DISPLAY_NAME = "YAC"
    }
}
