package com.posificator.ui

import androidx.compose.runtime.Composable

/** System back on Android; iOS has no back button, so it does nothing there. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
