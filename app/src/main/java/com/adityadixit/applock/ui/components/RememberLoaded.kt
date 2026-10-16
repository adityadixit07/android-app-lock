package com.adityadixit.applock.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Runs [load] once per [key] and returns its result (null until it completes). */
@Composable
fun <T : Any> rememberLoaded(key: Any, load: suspend () -> T?): T? {
    var value by remember(key) { mutableStateOf<T?>(null) }
    LaunchedEffect(key) { value = load() }
    return value
}
