package com.github.samg101developer.sppidea.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Application-wide S++ toolchain settings, e.g. the location of the `spp` compiler binary.
 * Configurable from Settings | Languages & Frameworks | S++.
 */
@Service(Service.Level.APP)
@State(name = "SppSettings", storages = [Storage("spp.xml", roamingType = RoamingType.PER_OS)])
class SppSettings : PersistentStateComponent<SppSettings.State> {

    class State {
        var sppExecutablePath: String = ""
    }

    private var myState = State()

    var sppExecutablePath: String
        get() = myState.sppExecutablePath
        set(value) {
            myState.sppExecutablePath = value
        }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        @JvmStatic
        fun getInstance(): SppSettings = service()
    }
}
