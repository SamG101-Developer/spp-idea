package com.github.samg101developer.sppidea.module

import com.intellij.lang.SppIcons
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import javax.swing.Icon

/** The "S++" entry in File | New | Project / Module, backed by [SppModuleBuilder]. */
class SppModuleType : ModuleType<SppModuleBuilder>(ID) {

    override fun createModuleBuilder(): SppModuleBuilder = SppModuleBuilder()

    override fun getName(): String = "S++"

    override fun getDescription(): String = "An S++ project, scaffolded with 'spp init'"

    override fun getNodeIcon(isOpened: Boolean): Icon = SppIcons.FILE

    companion object {
        const val ID = "SPP_MODULE_TYPE"

        @JvmStatic
        fun getInstance(): SppModuleType = ModuleTypeManager.getInstance().findByID(ID) as SppModuleType
    }
}
