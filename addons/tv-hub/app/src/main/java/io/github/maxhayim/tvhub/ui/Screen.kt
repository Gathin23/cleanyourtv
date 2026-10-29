package io.github.maxhayim.tvhub.ui

import android.view.View
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** One page of the main window. Collections run while the page is shown and stop when it is hidden. */
abstract class Screen(val host: MainActivity) {
    val ctx get() = host
    val repo get() = host.repo
    abstract val view: View
    private val jobs = ArrayList<Job>()

    open fun onShow() {}
    open fun onHide() { jobs.forEach { it.cancel() }; jobs.clear() }
    /** Moves focus into the page; false if it has nothing focusable. */
    abstract fun focusFirst(): Boolean

    protected fun <T> watch(flow: Flow<T>, block: (T) -> Unit) { jobs += host.scope.launch { flow.collect { block(it) } } }
    protected fun launch(block: suspend () -> Unit) { jobs += host.scope.launch { block() } }
}
