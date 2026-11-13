package com.github.samg101developer.sppidea.run

import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.openapi.components.StoredProperty

// The options class for the S++ run configuration, which
// stores the configuration's state (executable, modules
// directory, module name and program arguments).
class SppRunConfigurationOptions : LocatableRunConfigurationOptions() {
  private val modulesPathProperty: StoredProperty<String?> =
    string("").provideDelegate(this, "modulesPath")

  private val moduleNameProperty: StoredProperty<String?> =
    string("").provideDelegate(this, "moduleName")

  private val programArgumentsProperty: StoredProperty<String?> =
    string("").provideDelegate(this, "programArguments")

  private val executableNameProperty: StoredProperty<String?> =
    string("").provideDelegate(this, "executable")

  // The name of the `spp` executable (from the S++ settings) to
  // use; blank for the default one.
  var executableName: String?
    get() = executableNameProperty.getValue(this)
    set(value) {
      executableNameProperty.setValue(this, value ?: "")
    }

  var modulesPath: String?
    get() = modulesPathProperty.getValue(this)
    set(value) {
      modulesPathProperty.setValue(this, value ?: "")
    }

  var moduleName: String?
    get() = moduleNameProperty.getValue(this)
    set(value) {
      moduleNameProperty.setValue(this, value ?: "")
    }

  var programArguments: String?
    get() = programArgumentsProperty.getValue(this)
    set(value) {
      programArgumentsProperty.setValue(this, value ?: "")
    }
}
