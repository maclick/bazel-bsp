package org.jetbrains.bsp.bazel.projectview.model

import org.apache.logging.log4j.LogManager
import org.jetbrains.bsp.bazel.projectview.model.sections.ProjectViewBazelBinarySection
import org.jetbrains.bsp.bazel.projectview.model.sections.ProjectViewBuildFlagsSection
import org.jetbrains.bsp.bazel.projectview.model.sections.ProjectViewBuildManualTargetsSection
import org.jetbrains.bsp.bazel.projectview.model.sections.ProjectViewDeriveTargetsFromDirectoriesSection
import org.jetbrains.bsp.bazel.projectview.model.sections.ProjectViewDirectoriesSection
import org.jetbrains.bsp.bazel.projectview.model.sections.ProjectViewExcludableListSection
import org.jetbrains.bsp.bazel.projectview.model.sections.ProjectViewImportDepthSection
import org.jetbrains.bsp.bazel.projectview.model.sections.ProjectViewListSection
import org.jetbrains.bsp.bazel.projectview.model.sections.ProjectViewSingletonSection
import org.jetbrains.bsp.bazel.projectview.model.sections.ProjectViewTargetsSection

/**
 * Representation of the project view file.
 *
 * @link https://ij.bazel.build/docs/project-views.html
 */
data class ProjectView(
    /** targets included and excluded from the project  */
    val targets: ProjectViewTargetsSection?,
    /** bazel path used to invoke bazel from the code  */
    val bazelBinary: ProjectViewBazelBinarySection?,
    /** bazel flags added to all bazel command invocations  */
    val buildFlags: ProjectViewBuildFlagsSection?,
    /** flag for building manual targets. */
    val buildManualTargets: ProjectViewBuildManualTargetsSection?,
    /** directories included and excluded from the project  */
    val directories: ProjectViewDirectoriesSection?,
    /** if set to true, relevant project targets will be automatically derived from the `directories` */
    val deriveTargetsFromDirectories: ProjectViewDeriveTargetsFromDirectoriesSection?,
    /** level of depth for importing inherited targets */
    val importDepth: ProjectViewImportDepthSection?,
) {

    data class Builder(
        private val imports: List<ProjectView> = emptyList(),
        private val targets: ProjectViewTargetsSection? = null,
        private val bazelBinary: ProjectViewBazelBinarySection? = null,
        private val buildFlags: ProjectViewBuildFlagsSection? = null,
        private val buildManualTargets: ProjectViewBuildManualTargetsSection? = null,
        private val directories: ProjectViewDirectoriesSection? = null,
        private val deriveTargetsFromDirectories: ProjectViewDeriveTargetsFromDirectoriesSection? = null,
        private val importDepth: ProjectViewImportDepthSection? = null,
    ) {

        fun build(): ProjectView {
            log.debug("Building project view for: {}", this)

            return buildWithImports(imports)
        }

        private fun buildWithImports(importedProjectViews: List<ProjectView>): ProjectView {
            val targets = combineTargetsSection(importedProjectViews)
            val bazelBinary = combineBazelBinarySection(importedProjectViews)
            val buildFlags = combineBuildFlagsSection(importedProjectViews)
            val buildManualTargets = combineManualTargetsSection(importedProjectViews)
            val directories = combineDirectoriesSection(importedProjectViews)
            val deriveTargetsFromDirectories = combineDeriveTargetFlagSection(importedProjectViews)
            val importDepth = combineImportDepthSection(importedProjectViews)
            log.debug(
                "Building project view with combined"
                        + " targets: {},"
                        + " bazel binary: {},"
                        + " build flags: {}"
                        + " build manual targets {},"
                        + " directories: {},"
                        + " deriveTargetsFlag: {}."
                        + " import depth: {},",
                targets,
                bazelBinary,
                buildFlags,
                buildManualTargets,
                directories,
                deriveTargetsFromDirectories,
                importDepth,
            )
            return ProjectView(
                targets,
                bazelBinary,
                buildFlags,
                buildManualTargets,
                directories,
                deriveTargetsFromDirectories,
                importDepth,
            )
        }

        private fun combineTargetsSection(importedProjectViews: List<ProjectView>): ProjectViewTargetsSection? {
            val includedTargets = combineListValuesWithImported(
                importedProjectViews,
                targets,
                ProjectView::targets,
                ProjectViewTargetsSection::values
            )
            val excludedTargets = combineListValuesWithImported(
                importedProjectViews,
                targets,
                ProjectView::targets,
                ProjectViewTargetsSection::excludedValues
            )
            return createInstanceOfExcludableListSectionOrNull(
                includedTargets,
                excludedTargets,
                ::ProjectViewTargetsSection
            )
        }

        private fun combineBuildFlagsSection(importedProjectViews: List<ProjectView>): ProjectViewBuildFlagsSection? {
            val flags = combineListValuesWithImported(
                importedProjectViews,
                buildFlags,
                ProjectView::buildFlags,
                ProjectViewBuildFlagsSection::values,
            )

            return createInstanceOfListSectionOrNull(flags, ::ProjectViewBuildFlagsSection)
        }

        private fun combineDirectoriesSection(importedProjectViews: List<ProjectView>): ProjectViewDirectoriesSection? {
            val includedTargets = combineListValuesWithImported(
                importedProjectViews,
                directories,
                ProjectView::directories,
                ProjectViewDirectoriesSection::values
            )
            val excludedTargets = combineListValuesWithImported(
                importedProjectViews,
                directories,
                ProjectView::directories,
                ProjectViewDirectoriesSection::excludedValues
            )
            return createInstanceOfExcludableListSectionOrNull(
                includedTargets,
                excludedTargets,
                ::ProjectViewDirectoriesSection
            )
        }

        private fun <V, T : ProjectViewListSection<V>> combineListValuesWithImported(
            importedProjectViews: List<ProjectView>,
            section: T?,
            sectionGetter: (ProjectView) -> T?,
            valuesGetter: (T) -> List<V>
        ): List<V> {
            val sectionValues = section
                ?.let(valuesGetter)
                .orEmpty()

            val importedValues = importedProjectViews
                .mapNotNull(sectionGetter)
                .flatMap(valuesGetter)

            return importedValues + sectionValues
        }

        private fun <V, T : ProjectViewExcludableListSection<V>?> createInstanceOfExcludableListSectionOrNull(
            includedElements: List<V>,
            excludedElements: List<V>,
            constructor: (List<V>, List<V>) -> T
        ): T? = if (includedElements.isEmpty() && excludedElements.isEmpty()) null else constructor(
            includedElements,
            excludedElements
        )

        private fun <V, T : ProjectViewListSection<V>?> createInstanceOfListSectionOrNull(
            values: List<V>, constructor: (List<V>) -> T
        ): T? = if (values.isEmpty()) null else constructor(values)

        private fun combineBazelBinarySection(importedProjectViews: List<ProjectView>): ProjectViewBazelBinarySection? =
            bazelBinary ?: getLastImportedSingletonValue(importedProjectViews, ProjectView::bazelBinary)

        private fun combineManualTargetsSection(importedProjectViews: List<ProjectView>): ProjectViewBuildManualTargetsSection? =
            buildManualTargets
                ?: getLastImportedSingletonValue(importedProjectViews, ProjectView::buildManualTargets)

        private fun combineDeriveTargetFlagSection(importedProjectViews: List<ProjectView>): ProjectViewDeriveTargetsFromDirectoriesSection? =
            deriveTargetsFromDirectories ?: getLastImportedSingletonValue(
                importedProjectViews,
                ProjectView::deriveTargetsFromDirectories
            )

        private fun combineImportDepthSection(importedProjectViews: List<ProjectView>): ProjectViewImportDepthSection? =
            importDepth ?: getLastImportedSingletonValue(importedProjectViews, ProjectView::importDepth)

        private fun <T : ProjectViewSingletonSection<*>> getLastImportedSingletonValue(
            importedProjectViews: List<ProjectView>, sectionGetter: (ProjectView) -> T?
        ): T? = importedProjectViews.mapNotNull(sectionGetter).lastOrNull()
    }

    companion object {
        private val log = LogManager.getLogger(ProjectView::class.java)
    }
}
