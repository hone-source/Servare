package com.spiralsoft.servare.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// Los ViewModels lanzan en viewModelScope (Dispatchers.Main), que en la JVM no existe.
// Con el dispatcher sin cola las corrutinas corren de inmediato, hasta su primera suspensión
@OptIn(ExperimentalCoroutinesApi::class)
class ReglaDispatcherPrincipal(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
