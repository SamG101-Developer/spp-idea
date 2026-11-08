package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.run.SppBuildMode
import com.github.samg101developer.sppidea.run.SppConfigurationKind
import com.github.samg101developer.sppidea.run.SppConfigurationType
import com.github.samg101developer.sppidea.run.SppModeTarget
import com.github.samg101developer.sppidea.run.SppModeTargetProvider
import com.github.samg101developer.sppidea.run.SppRunProfile
import com.github.samg101developer.sppidea.run.sppCommandLine
import com.intellij.execution.DefaultExecutionTarget
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The build mode is chosen beside the run configuration, as an execution target. Source and test configurations both
 * run in either mode; with none chosen, each uses the mode "spp" defaults to for its command.
 */
class SppBuildModeTest : BasePlatformTestCase() {

    private fun configuration(kind: SppConfigurationKind): SppRunProfile {
        val type = ConfigurationTypeUtil.findConfigurationType(SppConfigurationType::class.java)
        val factory = type.configurationFactories.first { it.id == kind.factoryId }
        return factory.createTemplateConfiguration(project) as SppRunProfile
    }

    private fun targetNames(kind: SppConfigurationKind) =
        SppModeTargetProvider().getTargets(project, configuration(kind)).map { it.displayName }

    fun testSourceOffersBothModes() = assertEquals(listOf("dev", "rel"), targetNames(SppConfigurationKind.SOURCE))

    fun testTestOffersBothModes() = assertEquals(listOf("dev", "rel"), targetNames(SppConfigurationKind.TEST))

    fun testRunsOnlyOnModeTargets() {
        val source = configuration(SppConfigurationKind.SOURCE)
        val test = configuration(SppConfigurationKind.TEST)
        assertTrue(source.canRunOn(SppModeTarget.of(SppBuildMode.DEV)))
        assertTrue(test.canRunOn(SppModeTarget.of(SppBuildMode.DEV)))
        assertTrue(test.canRunOn(SppModeTarget.of(SppBuildMode.REL)))
        assertFalse(source.canRunOn(DefaultExecutionTarget.INSTANCE))
    }

    fun testModeComesFromTheTargetItRunsOn() {
        val source = configuration(SppConfigurationKind.SOURCE)
        assertEquals(SppBuildMode.REL, SppModeTarget.modeFor(source, SppModeTarget.of(SppBuildMode.REL)))
        assertEquals(SppBuildMode.DEV, SppModeTarget.modeFor(source, SppModeTarget.of(SppBuildMode.DEV)))
    }

    fun testTestRunsInTheChosenMode() {
        val test = configuration(SppConfigurationKind.TEST)
        assertEquals(SppBuildMode.DEV, SppModeTarget.modeFor(test, SppModeTarget.of(SppBuildMode.DEV)))
    }

    fun testWithNoModeChosenEachKindUsesItsDefault() {
        ExecutionTargetManager.getInstance(project).activeTarget = DefaultExecutionTarget.INSTANCE
        assertEquals(SppBuildMode.DEV, SppModeTarget.modeFor(configuration(SppConfigurationKind.SOURCE), null))
        assertEquals(SppBuildMode.REL, SppModeTarget.modeFor(configuration(SppConfigurationKind.TEST), null))
    }

    fun testCommandLinePassesTheMode() {
        val line = sppCommandLine("spp", SppConfigurationKind.SOURCE.runCommand, "/work", "--x", SppBuildMode.REL)
        assertEquals(listOf("run", "--mode", "rel", "--x"), line.parametersList.list)
        val test = sppCommandLine("spp", SppConfigurationKind.TEST.runCommand, "/work", null, SppBuildMode.DEV)
        assertEquals(listOf("test", "--mode", "dev"), test.parametersList.list)
    }
}
