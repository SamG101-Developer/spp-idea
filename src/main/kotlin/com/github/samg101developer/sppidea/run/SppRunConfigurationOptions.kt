package com.github.samg101developer.sppidea.run

import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.openapi.components.StoredProperty

/**
 * Persisted state of an [SppRunConfiguration].
 *
 * Each stored property is registered under the name of the *public* accessor that exposes it.
 * `BaseState` serialises a property only when its registered name matches an accessible accessor,
 * so a `private var x by string("")` fronted by a differently named public property is registered
 * as "x", never matched, and silently dropped on every save and on every clone the run
 * configuration dialog makes -- which also makes the dialog see no change and disable Apply.
 */
class SppRunConfigurationOptions : LocatableRunConfigurationOptions() {

    private val moduleNameProperty: StoredProperty<String?> =
        string("").provideDelegate(this, "moduleName")

    private val programArgumentsProperty: StoredProperty<String?> =
        string("").provideDelegate(this, "programArguments")

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
