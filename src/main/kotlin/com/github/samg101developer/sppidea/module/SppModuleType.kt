package com.github.samg101developer.sppidea.module

import com.intellij.lang.SppIcons
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import javax.swing.Icon

// The "S++" entry in File | New | Project / Module, backed by
// the module builder.
class SppModuleType : ModuleType<SppModuleBuilder>(ID) {

  // Create a new S++ module builder, which is used to create
  // new S++ projects and modules.
  override fun createModuleBuilder(): SppModuleBuilder = SppModuleBuilder()

  // The name of the module type, which is displayed in the
  // New Project / Module dialog.
  override fun getName(): String = "S++"

  // The description of the module type, which is displayed in
  // the New Project / Module dialog.
  override fun getDescription(): String = "An S++ project, scaffolded with 'spp init'"

  // The icon of the module type, which is displayed in the New
  // Project / Module dialog.
  override fun getNodeIcon(isOpened: Boolean): Icon = SppIcons.FILE

  // The companion object contains the ID of the module type and
  // a method to get the instance of the module type from the
  // ModuleTypeManager.
  companion object {
    const val ID = "SPP_MODULE_TYPE"

    @JvmStatic
    fun getInstance(): SppModuleType = ModuleTypeManager.getInstance().findByID(ID) as SppModuleType
  }
}
