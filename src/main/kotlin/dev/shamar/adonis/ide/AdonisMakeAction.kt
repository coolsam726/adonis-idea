package dev.shamar.adonis.ide

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

/**
 * Adonis menu → New… → `ace make:*` actions (scaffolder parity).
 * Interactive model dialog collects companion flags; everything else is a name prompt.
 */
class AdonisMakeActionGroup : DefaultActionGroup("New…", true), DumbAware {
    init {
        isPopup = true
        for (gen in AdonisMakeCatalog.ALL) {
            add(AdonisMakeAction(gen))
        }
    }
}

class AdonisMakeAction(
    private val generator: AdonisMakeCatalog.Generator,
) : AnAction(AdonisMakeCatalog.menuLabel(generator)), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val root = AdonisProjectService.getInstance(project).appRoot()
        if (root == null) {
            Messages.showErrorDialog(
                project,
                "Open an AdonisJS app (folder with adonisrc.ts / ace.js) first.",
                "Adonis",
            )
            return
        }

        val args = if (generator.id == "model" && generator.interactive) {
            val prompted = AdonisModelMakeDialog.prompt(project) ?: return
            AdonisMakeCatalog.modelAceArgs(prompted.first, prompted.second)
        } else {
            val name = if (generator.namePrompt != null) {
                Messages.showInputDialog(
                    project,
                    generator.namePrompt,
                    AdonisMakeCatalog.menuLabel(generator),
                    Messages.getQuestionIcon(),
                ) ?: return
            } else {
                null
            }
            if (generator.namePrompt != null && name.isNullOrBlank()) return
            AdonisMakeCatalog.byId(generator.id)?.aceArgs(name)
                ?: generator.aceArgs(name)
        }

        AdonisAceRunner.run(project, root, args)
        Messages.showInfoMessage(
            project,
            "Ran: ace $args\n(Index rebuild scheduled.)",
            "Adonis",
        )
    }
}
