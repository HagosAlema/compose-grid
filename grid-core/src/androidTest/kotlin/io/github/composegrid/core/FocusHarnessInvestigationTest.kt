package io.github.composegrid.core

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the one prerequisite every focus test in this module depends on: that
 * requesting focus works once Compose is in [InputMode.Keyboard].
 *
 * ## Why this exists
 * Focus assertions used to fail here for no visible reason, and the cause was
 * worth writing down. Android starts in [InputMode.Touch], and the focus target
 * inside `Modifier.clickable` — which is what the grid's cells use — declines
 * focus in touch mode. `requestFocus()` doesn't throw, the node simply keeps
 * `Focused = false`, and every assertion downstream fails.
 *
 * Three further findings from that investigation, deliberately *not* encoded as
 * tests because [InputMode] is window-global and leaks between tests in the
 * shared instrumentation process, which makes any touch-mode assertion
 * order-dependent and flaky:
 *
 *  - A bare `Modifier.focusable()` is **not** input-mode gated and takes focus
 *    in touch mode. A control test built on `focusable()` therefore "proves" the
 *    harness works while `clickable` still fails — that false negative is what
 *    made this take so long to pin down.
 *  - Driving a [androidx.compose.ui.focus.FocusRequester] from composition
 *    doesn't escape the gate either; it isn't the semantics action's fault.
 *  - An injected key event does not move Compose out of touch mode, unlike real
 *    hardware input, so there's no way around asking
 *    [androidx.compose.ui.platform.LocalInputModeManager] directly.
 *
 * See [setContentWithKeyboardInputMode], which is what tests should use.
 */
@RunWith(AndroidJUnit4::class)
class FocusHarnessInvestigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun requestingFocusWorksInKeyboardInputMode() {
        composeRule.setContentWithKeyboardInputMode {
            Box(Modifier.size(100.dp).clickable {}) { BasicText("target") }
        }

        composeRule.onNodeWithText("target").requestFocus()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("target").assertIsFocused()
    }
}
