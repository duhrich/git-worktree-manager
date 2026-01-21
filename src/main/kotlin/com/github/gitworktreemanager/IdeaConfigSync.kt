package com.github.gitworktreemanager

import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * Syncs .idea configuration from source worktree to target worktree.
 *
 * Symlinks directories that are safe to share:
 * - runConfigurations/ (run/debug configs)
 * - inspectionProfiles/ (inspection settings)
 * - codeStyles/ (code formatting)
 * - dictionaries/ (spell check)
 * - scopes/ (custom scopes)
 *
 * Copies files that should be initialized but independent:
 * - misc.xml (SDK settings)
 * - *.iml (module files)
 */
object IdeaConfigSync {

    private val LOG = Logger.getInstance(IdeaConfigSync::class.java)

    // Directories safe to symlink (shared across worktrees)
    private val SYMLINK_DIRS = listOf(
        "runConfigurations",
        "inspectionProfiles",
        "codeStyles",
        "dictionaries",
        "scopes"
    )

    // Files to copy once if they don't exist
    private val COPY_FILES = listOf(
        "misc.xml"
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

        // Copy files (only if target doesn't exist)
        for (file in COPY_FILES) {
            val sourceFile = sourceIdea.resolve(file)
            val targetFile = targetIdea.resolve(file)

            if (Files.exists(sourceFile) && !Files.exists(targetFile)) {
                try {
                    Files.copy(sourceFile, targetFile)
                    LOG.info("Copied $file to $targetIdea")
                } catch (e: Exception) {
                    LOG.warn("Failed to copy $file: ${e.message}")
                }
            }
        }

        // Copy .iml files
        try {
            Files.list(sourceIdea).use { stream ->
                stream.filter { it.toString().endsWith(".iml") }
                    .forEach { sourceIml ->
                        val targetIml = targetIdea.resolve(sourceIml.fileName)
                        if (!Files.exists(targetIml)) {
                            Files.copy(sourceIml, targetIml)
                            LOG.info("Copied ${sourceIml.fileName} to $targetIdea")
                        }
                    }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to copy .iml files: ${e.message}")
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
