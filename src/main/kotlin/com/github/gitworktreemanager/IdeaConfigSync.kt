package com.github.gitworktreemanager

import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * Syncs .idea configuration from source worktree to target worktree.
 *
 * Symlinks most config so changes are shared across worktrees.
 * Only workspace.xml (window state, open files) stays independent.
 */
object IdeaConfigSync {

    private val LOG = Logger.getInstance(IdeaConfigSync::class.java)

    // Directories to symlink (shared across worktrees)
    private val SYMLINK_DIRS = listOf(
        "runConfigurations",
        "inspectionProfiles",
        "codeStyles",
        "dictionaries",
        "scopes",
        "libraries",        // project libraries
        "artifacts",        // build artifacts config
        "dataSources",      // database connections
        "sqldialects"       // SQL dialect settings
    )

    // Files to symlink (shared across worktrees)
    private val SYMLINK_FILES = listOf(
        "misc.xml",         // SDK/interpreter settings
        "modules.xml",      // module list
        "vcs.xml",          // version control settings
        "encodings.xml",    // file encodings
        "compiler.xml",     // compiler settings
        "jarRepositories.xml",
        "kotlinc.xml",
        "externalDependencies.xml"
    )

    // Files to NEVER symlink (workspace-specific)
    private val SKIP_FILES = setOf(
        "workspace.xml",    // window state, open files, breakpoints
        "tasks.xml",        // task tracking
        "usage.statistics.xml",
        "sonarlint.xml"
    )

    fun syncConfig(sourcePath: Path, targetPath: Path) {
        val sourceIdea = sourcePath.resolve(".idea")
        val targetIdea = targetPath.resolve(".idea")

        if (!Files.exists(sourceIdea)) {
            LOG.info("No .idea folder in source: $sourceIdea")
            return
        }

        // Create .idea in target if needed
        if (!Files.exists(targetIdea)) {
            Files.createDirectories(targetIdea)
        }

        // Symlink directories
        for (dir in SYMLINK_DIRS) {
            val sourceDir = sourceIdea.resolve(dir)
            val targetDir = targetIdea.resolve(dir)

            if (Files.exists(sourceDir) && Files.isDirectory(sourceDir)) {
                createSymlink(sourceDir, targetDir)
            }
        }

        // Symlink specific config files
        for (file in SYMLINK_FILES) {
            val sourceFile = sourceIdea.resolve(file)
            val targetFile = targetIdea.resolve(file)

            if (Files.exists(sourceFile)) {
                createSymlink(sourceFile, targetFile)
            }
        }

        // Symlink .iml files
        try {
            Files.list(sourceIdea).use { stream ->
                stream.filter { it.toString().endsWith(".iml") }
                    .forEach { sourceIml ->
                        val targetIml = targetIdea.resolve(sourceIml.fileName)
                        createSymlink(sourceIml, targetIml)
                    }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to symlink .iml files: ${e.message}")
        }
    }

    private fun createSymlink(source: Path, target: Path) {
        try {
            // If target exists and is not a symlink, skip (don't overwrite user's config)
            if (Files.exists(target)) {
                if (Files.isSymbolicLink(target)) {
                    // Already a symlink - check if it points to the right place
                    val existingTarget = Files.readSymbolicLink(target)
                    if (existingTarget == source || target.parent.resolve(existingTarget).normalize() == source.normalize()) {
                        return // Already correct
                    }
                    Files.delete(target) // Wrong symlink, recreate
                } else {
                    LOG.info("Skipping ${target.fileName} - already exists as regular dir/file")
                    return
                }
            }

            Files.createSymbolicLink(target, source)
            LOG.info("Created symlink: $target -> $source")
        } catch (e: Exception) {
            LOG.warn("Failed to create symlink for ${source.fileName}: ${e.message}")
        }
    }
}
