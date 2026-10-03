package dev.shamar.adonis.ide

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor

/**
 * Plugin unload hook. Native index needs no process teardown; keep the listener
 * so ``require-restart`` stays paired with a clean DynamicPluginListener.
 */
class AdonisPluginListener : DynamicPluginListener {
    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        // Index services dispose with the project; nothing to stop.
    }

    companion object {
        const val PLUGIN_ID: String = "dev.shamar.adonis.ide"
    }
}
