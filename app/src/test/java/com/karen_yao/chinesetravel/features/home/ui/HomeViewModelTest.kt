package com.karen_yao.chinesetravel.features.home.ui

import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.SnapRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialStateIsLoading() {
        val viewModel = HomeViewModel(FakeSnapRepository())

        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.snaps.isEmpty())
    }

    @Test
    fun emptyEmissionProducesEmptyState() = runTest {
        val viewModel = HomeViewModel(FakeSnapRepository())
        collectState(viewModel)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isEmpty)
        assertEquals(0, viewModel.uiState.value.snapCount)
    }

    @Test
    fun nonEmptyEmissionProducesListAndCount() = runTest {
        val snaps = listOf(snap("one"), snap("two"))
        val viewModel = HomeViewModel(FakeSnapRepository(snaps))
        collectState(viewModel)

        advanceUntilIdle()

        assertEquals(snaps, viewModel.uiState.value.snaps)
        assertEquals(2, viewModel.uiState.value.snapCount)
        assertFalse(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun deleteCallsRepositoryAndEmitsSuccess() = runTest {
        val target = snap("delete-me")
        val repository = FakeSnapRepository(listOf(target))
        val viewModel = HomeViewModel(repository)
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.deleteSnap(target)
        advanceUntilIdle()

        assertEquals(1, repository.deleteCalls)
        assertTrue(viewModel.uiState.value.snaps.isEmpty())
        assertFalse(viewModel.uiState.value.isMutating)
        assertEquals(
            R.string.home_delete_success,
            (viewModel.effects.first() as HomeEffect.ShowMessage).message
        )
    }

    @Test
    fun deleteFailureClearsMutationAndEmitsError() = runTest {
        val target = snap("delete-me")
        val repository = FakeSnapRepository(listOf(target)).apply { failDelete = true }
        val viewModel = HomeViewModel(repository)
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.deleteSnap(target)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isMutating)
        assertEquals(
            R.string.home_delete_error,
            (viewModel.effects.first() as HomeEffect.ShowMessage).message
        )
    }

    @Test
    fun clearAllHandlesSuccessAndFailure() = runTest {
        val successRepository = FakeSnapRepository(listOf(snap("one")))
        val successViewModel = HomeViewModel(successRepository)
        collectState(successViewModel)
        advanceUntilIdle()

        successViewModel.clearAllSnaps()
        advanceUntilIdle()

        assertEquals(1, successRepository.clearCalls)
        assertTrue(successViewModel.uiState.value.isEmpty)
        assertEquals(
            R.string.home_clear_success,
            (successViewModel.effects.first() as HomeEffect.ShowMessage).message
        )

        val failureRepository = FakeSnapRepository(listOf(snap("two"))).apply { failClear = true }
        val failureViewModel = HomeViewModel(failureRepository)
        collectState(failureViewModel)
        advanceUntilIdle()

        failureViewModel.clearAllSnaps()
        advanceUntilIdle()

        assertEquals(1, failureRepository.clearCalls)
        assertFalse(failureViewModel.uiState.value.isMutating)
        assertEquals(
            R.string.home_clear_error,
            (failureViewModel.effects.first() as HomeEffect.ShowMessage).message
        )
    }

    @Test
    fun repeatedMutationIsIgnoredWhileOperationIsActive() = runTest {
        val target = snap("one")
        val repository = FakeSnapRepository(listOf(target)).apply {
            deleteGate = CompletableDeferred()
        }
        val viewModel = HomeViewModel(repository)
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.deleteSnap(target)
        viewModel.deleteSnap(target)
        runCurrent()

        assertEquals(1, repository.deleteCalls)
        assertTrue(viewModel.uiState.value.isMutating)

        repository.deleteGate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isMutating)
    }

    private fun TestScope.collectState(viewModel: HomeViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    private fun snap(id: String) = PlaceSnap(
        id = id,
        imagePath = "/tmp/$id.jpg",
        nameCn = id,
        namePinyin = id,
        lat = null,
        longitude = null,
        address = null,
        translation = id,
        googleMapsLink = null,
        createdAt = 1L
    )
}

private class FakeSnapRepository(
    initialSnaps: List<PlaceSnap> = emptyList()
) : SnapRepository {
    private val snaps = MutableStateFlow(initialSnaps)

    var deleteCalls = 0
    var clearCalls = 0
    var failDelete = false
    var failClear = false
    var deleteGate: CompletableDeferred<Unit>? = null

    override fun getAllSnaps(): Flow<List<PlaceSnap>> = snaps

    override suspend fun deleteSnap(snap: PlaceSnap) {
        deleteCalls++
        deleteGate?.await()
        if (failDelete) error("delete failed")
        snaps.update { current -> current.filterNot { it.id == snap.id } }
    }

    override suspend fun clearAllSnaps() {
        clearCalls++
        if (failClear) error("clear failed")
        snaps.value = emptyList()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
