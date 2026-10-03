package dev.shamar.adonis.ide

/**
 * Pure threading policy for the Node indexer.
 *
 * IntelliJ forbids waiting on [com.intellij.execution.process.OSProcessHandler]
 * on the EDT **or** under a ReadAction (highlighting, references, completion).
 * Cold [AdonisProjectService.index] calls must defer to a background rebuild.
 */
object AdonisIndexThreading {
    /**
     * When true, [AdonisProjectService.index] must schedule a background rebuild
     * and return a placeholder instead of calling [AdonisIndexProcess.run].
     */
    fun shouldDeferRebuild(
        isDispatchThread: Boolean,
        isUnitTestMode: Boolean,
        isReadAccessAllowed: Boolean,
    ): Boolean {
        if (isUnitTestMode) return false
        return isDispatchThread || isReadAccessAllowed
    }
}
