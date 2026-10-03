package dev.shamar.adonis.ide

/**
 * Pure threading policy for the Node indexer.
 *
 * IntelliJ forbids waiting on [com.intellij.execution.process.OSProcessHandler]
 * from the EDT — cold rebuilds must be deferred to a background thread.
 */
object AdonisIndexThreading {
    /**
     * When true, [AdonisProjectService.index] must schedule a background rebuild
     * and return a placeholder instead of calling [AdonisIndexProcess.run].
     */
    fun shouldDeferRebuild(isDispatchThread: Boolean, isUnitTestMode: Boolean): Boolean =
        isDispatchThread && !isUnitTestMode
}
