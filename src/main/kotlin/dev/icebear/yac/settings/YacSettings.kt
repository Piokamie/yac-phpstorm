package dev.icebear.yac.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

@Service(Service.Level.PROJECT)
@State(name = "YacSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class YacSettings : SimplePersistentStateComponent<YacSettings.Options>(Options()) {
    class Options : BaseState() {
        var yacPath by string("")
        var phpPath by string("")
        var shouldShowInlays by property(true)
    }

    val yacPath: String
        get() = state.yacPath.orEmpty().trim()

    val phpPath: String
        get() = state.phpPath.orEmpty().trim()

    val shouldShowInlays: Boolean
        get() = state.shouldShowInlays
}
