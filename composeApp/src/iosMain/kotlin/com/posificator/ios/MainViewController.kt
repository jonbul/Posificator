package com.posificator.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.posificator.ui.App
import platform.UIKit.UIViewController

@Suppress("FunctionName", "unused") // Called from Swift as MainViewControllerKt.MainViewController().
fun MainViewController(): UIViewController =
    ComposeUIViewController { App(AppGraph.repository, AppGraph.platform) }
