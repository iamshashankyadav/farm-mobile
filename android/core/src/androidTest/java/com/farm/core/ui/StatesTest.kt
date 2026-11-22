package com.farm.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farm.core.api.ApiException
import com.farm.core.api.NetworkException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shared Loading / Empty / Error states every data screen uses (#47). */
@RunWith(AndroidJUnit4::class)
class StatesTest {
    @get:Rule val rule = createComposeRule()

    @Test fun errorShowsTheServerMessageAndRetries() {
        var retries = 0
        rule.setContent { FarmTheme { ErrorState(ApiException("Listing not available", "LISTING_NOT_AVAILABLE", 409), { retries++ }, title = "Could not load produce") } }
        rule.onNodeWithText("Could not load produce").assertIsDisplayed()
        rule.onNodeWithText("Listing not available").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
        rule.onNodeWithText("Change server").assertDoesNotExist()
    }

    @Test fun offlineSaysCannotReachTheServerAndOffersChangeServer() {
        rule.setContent { FarmTheme { ErrorState(NetworkException(), {}) } }
        rule.onNodeWithText("Cannot reach the server").assertIsDisplayed()
        rule.onNodeWithText("Change server").assertIsDisplayed()
    }

    @Test fun emptySaysWhatToDoNext() {
        var widened = false
        rule.setContent { FarmTheme { EmptyState("No produce found", "Widen the radius.", action = "Widen radius", onAction = { widened = true }) } }
        rule.onNodeWithText("Widen the radius.").assertIsDisplayed()
        rule.onNodeWithText("Widen radius").performClick()
        assertEquals(true, widened)
    }

    @Test fun loadingIsASkeletonNotABlankScreen() {
        rule.setContent { FarmTheme { LoadingSkeleton() } }
        rule.onNodeWithText("Retry").assertDoesNotExist()
        rule.onRoot().assertIsDisplayed()
    }
}
