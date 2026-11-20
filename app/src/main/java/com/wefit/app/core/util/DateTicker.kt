package com.wefit.app.core.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/** Emits the current local date and re-emits when it changes (e.g. at midnight). */
fun currentDateFlow(pollMillis: Long = 30_000): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now())
        delay(pollMillis)
    }
}.distinctUntilChanged()
