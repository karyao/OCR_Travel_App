package com.karen_yao.chinesetravel.features.map.ui

import androidx.lifecycle.ViewModelStore
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.LocatedSnapSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun initialLoadingIsNotAnEmptyCollection() {
        val model = create(MutableStateFlow(emptyList()))
        assertTrue(model.uiState.value.isLoading)
        assertFalse(model.uiState.value.isEmpty)
        assertFalse(model.uiState.value.loadFailed)
    }

    @Test fun emptyAndPopulatedEmissionsUpdateMarkerState() = runTest(dispatcher) {
        val source = MutableStateFlow<List<PlaceSnap>>(emptyList())
        val model = create(source)
        collect(model)
        runCurrent()
        assertTrue(model.uiState.value.isEmpty)
        source.value = listOf(snap())
        runCurrent()
        val pin = model.uiState.value.pins.single()
        assertEquals(49.2, pin.latitude, 0.0)
        assertEquals(-123.1, pin.longitude, 0.0)
        assertEquals("测试文本", pin.title)
        assertEquals("📖 cè shì\n🌐 Test text\n📍 Address", pin.snippet)
        source.value = emptyList()
        runCurrent()
        assertTrue(model.uiState.value.isEmpty)
    }

    @Test fun invalidCoordinatesNeverProducePinsAndMissingDetailsHaveNoEmptyLines() = runTest(dispatcher) {
        val valid = snap().copy(lat = 0.0, longitude = 0.0, namePinyin = "", address = null)
        val source = MutableStateFlow(listOf(
            valid, snap().copy(lat = null), snap().copy(longitude = null),
            snap().copy(lat = Double.NaN), snap().copy(longitude = Double.POSITIVE_INFINITY),
            snap().copy(lat = 91.0), snap().copy(longitude = -181.0)
        ))
        val model = create(source)
        collect(model)
        runCurrent()
        assertEquals(1, model.uiState.value.pins.size)
        assertEquals("🌐 Test text", model.uiState.value.pins.single().snippet)
    }

    @Test fun repositoryFailureIsAnErrorRatherThanAnEmptyMap() = runTest(dispatcher) {
        val model = create(flow { error("database unavailable") })
        collect(model)
        runCurrent()
        assertTrue(model.uiState.value.loadFailed)
        assertFalse(model.uiState.value.isLoading)
        assertFalse(model.uiState.value.isEmpty)
    }

    private fun create(flow: Flow<List<PlaceSnap>>) = MapViewModel(LocatedSnapSource { flow }).also {
        store.put("map", it)
    }

    private fun TestScope.collect(model: MapViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect { } }
    }

    private fun snap() = PlaceSnap(
        imagePath = "", nameCn = "测试文本", namePinyin = "cè shì", lat = 49.2,
        longitude = -123.1, address = "Address", translation = "Test text", googleMapsLink = null
    )
}
