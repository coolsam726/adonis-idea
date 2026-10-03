package dev.shamar.adonis.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

/**
 * Invalidate the cached index when relevant project files change.
 */
class AdonisIndexWatcher : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        val connection = project.messageBus.connect()
        VirtualFileManager.getInstance().addAsyncFileListener(
            AsyncFileListener { events ->
                object : AsyncFileListener.ChangeApplier {
                    override fun afterVfsChange() {
                        if (events.any { relevant(it) }) {
                            AdonisProjectService.getInstance(project).invalidate()
                        }
                    }
                }
            },
            connection,
        )
        // Warm the index in the background on open.
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            AdonisProjectService.getInstance(project).rebuild()
        }
    }

    private fun relevant(event: VFileEvent): Boolean {
        val file = event.file ?: return false
        return AdonisProjectService.isIndexRelevant(file)
    }
}
