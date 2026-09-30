package com.karen_yao.chinesetravel.features.textselection.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.karen_yao.chinesetravel.core.workflow.CapturedPlaceSaver
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TextSelectionViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var root: File
    private lateinit var photo: File
    private var calls = 0
    private var capturedText: String? = null
    private var capturedFile: File? = null
    private var save: suspend () -> Int = { 7 }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        root = temporary.newFolder("managed")
        photo = File(root, "photo.jpg").apply { writeText("fixture") }
    }
    @After fun tearDown() {
        stores.forEach { it.clear() }
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    private fun handle(owned: Boolean = true, file: File = photo, initial: String = "") = SavedStateHandle(mapOf(
        TextSelectionViewModel.ARG_LINES to arrayOf("八邑酒楼", "永庆坊"),
        TextSelectionViewModel.ARG_IMAGE_PATH to file.absolutePath,
        TextSelectionViewModel.ARG_SELECTED_TEXT to initial,
        TextSelectionViewModel.ARG_OWNED to owned
    ))
    private fun model(handle: SavedStateHandle = handle()): TextSelectionViewModel {
        val vm = TextSelectionViewModel(handle, TextSelectionDependencies(
            root, CapturedPlaceSaver { text, file ->
                calls++
                capturedText = text
                capturedFile = file
                save()
            }, dispatcher
        ))
        stores += ViewModelStore().apply { put("selection", vm) }
        return vm
    }
    private fun restored(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    @Test fun selectionStartsEmptyAndRejectsInvalidIndices() {
        val vm = model()
        assertFalse(vm.uiState.value.confirmEnabled)
        vm.select(-1)
        vm.select(2)
        vm.confirm()
        assertEquals(-1, vm.uiState.value.selectedIndex)
        assertEquals(0, calls)
        vm.select(1)
        assertTrue(vm.uiState.value.confirmEnabled)
        vm.select(0)
        assertEquals(0, vm.uiState.value.selectedIndex)
    }
    @Test fun matchingInitialTextIsSelected() {
        assertEquals(1, model(handle(initial = "永庆坊")).uiState.value.selectedIndex)
    }
    @Test fun selectedIndexRestoresInNewViewModel() {
        val state = handle()
        model(state).select(1)
        assertEquals(1, model(restored(state)).uiState.value.selectedIndex)
    }
    @Test fun rapidConfirmationAndCancellationDuringSaveAreIgnored() = runTest(dispatcher) {
        val gate = CompletableDeferred<Int>()
        save = { gate.await() }
        val vm = model()
        vm.select(1)
        repeat(3) { vm.confirm() }
        vm.cancel()
        vm.select(0)
        assertEquals(TextSelectionStatus.SAVING, vm.uiState.value.status)
        assertEquals(1, vm.uiState.value.selectedIndex)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, calls)
        assertEquals("永庆坊", capturedText)
        assertEquals(photo, capturedFile)
        assertTrue(vm.uiState.value.commands.isEmpty())
        gate.complete(7)
        dispatcher.scheduler.runCurrent()
        assertEquals(TextSelectionStatus.SAVED, vm.uiState.value.status)
        assertEquals(TextSelectionEffect.SavedAndGoHome(7), vm.uiState.value.commands.single().effect)
        stores.last().clear()
        dispatcher.scheduler.runCurrent()
        assertTrue(photo.exists())
    }
    @Test fun saveFailureRetainsImageAndPreventsUncertainRetry() = runTest(dispatcher) {
        save = { error("may have committed") }
        val vm = model()
        vm.select(0)
        vm.confirm()
        dispatcher.scheduler.runCurrent()
        assertEquals(TextSelectionStatus.OUTCOME_UNKNOWN, vm.uiState.value.status)
        assertTrue(vm.uiState.value.exitEnabled)
        vm.confirm()
        vm.cancel()
        dispatcher.scheduler.runCurrent()
        assertEquals(1, calls)
        assertTrue(photo.exists())
        assertEquals(TextSelectionEffect.GoBack, vm.uiState.value.commands.single().effect)
    }
    @Test fun saverCancellationRetainsImageAndPreventsRetry() = runTest(dispatcher) {
        save = { throw CancellationException("uncertain commit") }
        val vm = model()
        vm.select(0)
        vm.confirm()
        dispatcher.scheduler.runCurrent()
        assertEquals(TextSelectionStatus.OUTCOME_UNKNOWN, vm.uiState.value.status)
        vm.confirm()
        assertEquals(1, calls)
        assertTrue(photo.exists())
    }
    @Test fun restoredInProgressSaveIsUnknownAndNeverRestarted() = runTest(dispatcher) {
        save = { awaitCancellation() }
        val state = handle()
        val original = model(state)
        original.select(0)
        original.confirm()
        dispatcher.scheduler.runCurrent()
        val recovered = model(restored(state))
        assertEquals(TextSelectionStatus.OUTCOME_UNKNOWN, recovered.uiState.value.status)
        assertEquals(0, recovered.uiState.value.selectedIndex)
        recovered.confirm()
        dispatcher.scheduler.runCurrent()
        assertEquals(1, calls)
        recovered.cancel()
        stores.forEach { it.clear() }
        dispatcher.scheduler.runCurrent()
        assertTrue(photo.exists())
    }
    @Test fun restoredSuccessNavigatesWithoutSavingAgain() = runTest(dispatcher) {
        val state = handle()
        val vm = model(state)
        vm.select(0)
        vm.confirm()
        dispatcher.scheduler.runCurrent()
        val recoveredState = restored(state)
        val recovered = model(recoveredState)
        assertEquals(TextSelectionEffect.SavedAndGoHome(7), recovered.uiState.value.commands.single().effect)
        recovered.confirm()
        assertEquals(1, calls)
        recovered.acknowledge(recovered.uiState.value.commands.single().id)
        assertTrue(model(restored(recoveredState)).uiState.value.commands.isEmpty())
    }

    @Test fun repeatedCancelQueuesOnlyOneExitAndDeletesOwnedPhoto() = runTest(dispatcher) {
        val vm = model()
        repeat(3) { vm.cancel(); vm.confirm(); vm.select(0) }
        dispatcher.scheduler.runCurrent()
        assertFalse(photo.exists())
        assertEquals(1, vm.uiState.value.commands.size)
        assertEquals(TextSelectionEffect.GoBack, vm.uiState.value.commands.single().effect)
        assertEquals(0, calls)
    }
    @Test fun cancellationNeverDeletesUnownedManagedPhoto() = runTest(dispatcher) {
        val vm = model(handle(owned = false))
        vm.cancel()
        stores.last().clear()
        dispatcher.scheduler.runCurrent()
        assertTrue(photo.exists())
    }
    @Test fun cancellationNeverDeletesExternalPhotoEvenIfMarkedOwned() = runTest(dispatcher) {
        val external = temporary.newFile("external.jpg")
        model(handle(file = external)).cancel()
        dispatcher.scheduler.runCurrent()
        assertTrue(external.exists())
    }
    @Test fun clearingViewModelDeletesOnlyUnsavedOwnedImage() = runTest(dispatcher) {
        model()
        stores.last().clear()
        dispatcher.scheduler.runCurrent()
        assertFalse(photo.exists())
    }
    @Test fun clearingViewModelDuringSaveRetainsImage() = runTest(dispatcher) {
        save = { awaitCancellation() }
        val vm = model()
        vm.select(0)
        vm.confirm()
        dispatcher.scheduler.runCurrent()
        stores.last().clear()
        dispatcher.scheduler.runCurrent()
        assertTrue(photo.exists())
    }
    @Test fun acknowledgementMustMatchHeadAndDoesNotReplayExitAfterRestore() {
        val state = handle()
        val vm = model(state)
        vm.cancel()
        val command = vm.uiState.value.commands.single()
        vm.acknowledge(command.id + 1)
        assertEquals(1, vm.uiState.value.commands.size)
        vm.acknowledge(command.id)
        assertTrue(model(restored(state)).uiState.value.commands.isEmpty())
    }
    @Test fun invalidArgumentsCannotStartSavingAndAllowExit() {
        val vm = model(SavedStateHandle())
        assertEquals(TextSelectionStatus.INVALID_INPUT, vm.uiState.value.status)
        assertFalse(vm.uiState.value.confirmEnabled)
        assertTrue(vm.uiState.value.exitEnabled)
        vm.confirm()
        assertEquals(0, calls)
    }
}
