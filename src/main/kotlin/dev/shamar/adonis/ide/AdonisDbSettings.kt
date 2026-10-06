package dev.shamar.adonis.ide

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Project settings for Laravel IDEA–style column completion sources.
 */
@Service(Service.Level.PROJECT)
@State(name = "AdonisDbSettings", storages = [Storage("adonisjs.xml")])
class AdonisDbSettings : PersistentStateComponent<AdonisDbSettings.State> {
    data class State(
        var columnSource: String = AdonisColumnSource.BOTH.name,
        /** Display name of the selected Database tool DataSource. */
        var dataSourceName: String = "",
        /** Stable unique id when the Database plugin provides one. */
        var dataSourceUniqueId: String = "",
    )

    private var state = State()

    var columnSource: AdonisColumnSource
        get() = AdonisColumnSource.fromStorage(state.columnSource)
        set(value) {
            state.columnSource = value.name
        }

    var dataSourceName: String
        get() = state.dataSourceName
        set(value) {
            state.dataSourceName = value
        }

    var dataSourceUniqueId: String
        get() = state.dataSourceUniqueId
        set(value) {
            state.dataSourceUniqueId = value
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(project: Project): AdonisDbSettings = project.service()
    }
}
