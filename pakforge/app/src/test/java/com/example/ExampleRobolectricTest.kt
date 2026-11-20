package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PakForge", appName)
  }

  @Test
  fun `main activity launches and navigates without ActivityResultRegistryOwner crash`() {
    composeTestRule.onNodeWithTag("upload_package_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("self_test_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_settings").performClick()
    composeTestRule.onNodeWithTag("lang_chip_ar").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("lang_chip_ar").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_forge").performClick()
    composeTestRule.onNodeWithTag("upload_package_button").performScrollTo().assertIsDisplayed()
  }
}
