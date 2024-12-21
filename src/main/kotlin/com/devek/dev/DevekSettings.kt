package com.devek.dev

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "DevekSettings",
    storages = [Storage("devek.xml")]
)
@Service
class DevekSettings : PersistentStateComponent<DevekSettings.State> {
    data class State(var authToken: String? = null)
    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) {
        myState = state
    }
}