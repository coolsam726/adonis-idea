package dev.shamar.adonis.ide

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

/** Adonis → Rebuild Index */
class RebuildIndexAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Rebuilding Adonis index",
            false,
        ) {
            @Volatile
            private var index: AdonisIndex? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Running Adonis indexer…"
                index = AdonisProjectService.getInstance(project).rebuild()
            }

            override fun onSuccess() {
                val built = index ?: return
                if (built.ok) {
                    Messages.showInfoMessage(
                        project,
                        "Indexed ${built.views.size} views, ${built.routes.size} routes, " +
                            "${built.configKeys.size} config keys.",
                        "Adonis Index",
                    )
                } else {
                    Messages.showErrorDialog(
                        project,
                        built.error ?: "Index rebuild failed.",
                        "Adonis Index",
                    )
                }
            }
        })
    }
}
