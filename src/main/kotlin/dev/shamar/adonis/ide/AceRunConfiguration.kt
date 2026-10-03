package dev.shamar.adonis.ide

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

class AceConfigurationType : ConfigurationTypeBase(
    "AdonisAce",
    "Adonis Ace",
    "Run a Node Ace CLI command",
    NotNullLazyValue.createValue { EdgeFileType.INSTANCE.icon },
) {
    init {
        addFactory(AceConfigurationFactory(this))
    }
}

class AceConfigurationFactory(type: AceConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "AdonisAceFactory"

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return AceRunConfiguration(project, this, "ace serve")
    }
}

class AceRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : RunConfigurationBase<RunConfigurationOptions>(project, factory, name) {

    var aceArgs: String = "serve"

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return AceSettingsEditor()
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                val guessed = project.guessProjectDir()?.toNioPath()
                val root = guessed?.let { AdonisProjectService.findAppRoot(it) } ?: guessed
                val args = aceArgs.split(" ").filter { it.isNotBlank() }
                val cmd = buildAceCommand(root, args)
                if (root != null) {
                    cmd.withWorkDirectory(root.toFile())
                }
                val handler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(cmd)
                ProcessTerminatedListener.attach(handler)
                return handler
            }
        }
    }
}

class AceSettingsEditor : SettingsEditor<AceRunConfiguration>() {
    private val argsField = JBTextField("serve")

    override fun resetEditorFrom(configuration: AceRunConfiguration) {
        argsField.text = configuration.aceArgs
    }

    override fun applyEditorTo(configuration: AceRunConfiguration) {
        configuration.aceArgs = argsField.text.trim()
    }

    override fun createEditor(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Ace arguments", argsField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
}

/**
 * Build `node ace.js <args>` (or `node ace <args>`) with working directory = app root.
 */
internal fun buildAceCommand(root: Path?, args: List<String>): GeneralCommandLine {
    val node = AdonisNode.resolveBinary(root)
    if (root != null) {
        val aceJs = root.resolve("ace.js")
        if (Files.isRegularFile(aceJs)) {
            return GeneralCommandLine(node, aceJs.toAbsolutePath().toString()).withParameters(args)
        }
        val ace = root.resolve("ace")
        if (Files.isRegularFile(ace)) {
            return GeneralCommandLine(node, ace.toAbsolutePath().toString()).withParameters(args)
        }
    }
    return GeneralCommandLine(node, "ace").withParameters(args)
}
