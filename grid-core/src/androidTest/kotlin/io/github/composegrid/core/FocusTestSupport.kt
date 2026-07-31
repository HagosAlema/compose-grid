package io.github.composegrid.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/**
 * Sets test content and switches Compose into [InputMode.Keyboard].
 *
 * Use this for anything focus-related. Android starts in [InputMode.Touch], and
 * the focus target inside `Modifier.clickable` declines focus in touch mode —
 * focus is a keyboard-mode concept there. The failure is silent: `requestFocus()`
 * doesn't throw, the node keeps `Focused = false`, and every focus assertion
 * fails for what looks like no reason.
 *
 * Two details worth knowing, both established by experiment:
 *  - A bare `Modifier.focusable()` *does* take focus in touch mode, so a control
 *    test built on `focusable()` rather than `clickable` will mislead you into
 *    thinking the harness is fine.
 *  - Injecting a key event first does **not** flip the input mode, so there's no
 *    way around asking [InputModeManager] directly.
 *
 * Note that the input mode is window-global and leaks between tests in the
 * shared instrumentation process. Always switch it explicitly, as this helper
 * does, rather than assuming a starting mode — a test that asserts *touch*-mode
 * behaviour will pass or fail depending on what ran before it.
 */
fun ComposeContentTestRule.setContentWithKeyboardInputMode(content: @Composable () -> Unit) {
    var inputModeManager: InputModeManager? = null
    setContent {
        inputModeManager = LocalInputModeManager.current
        content()
    }
    val manager = requireNotNull(inputModeManager) { "content did not compose" }
    runOnUiThread { manager.requestInputMode(InputMode.Keyboard) }
    waitForIdle()
}
