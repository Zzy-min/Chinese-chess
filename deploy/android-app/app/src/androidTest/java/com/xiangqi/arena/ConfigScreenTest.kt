package com.xiangqi.arena

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Rule
import org.junit.Test

class AppLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsWebContentHostInsteadOfEnvironmentPicker() {
        composeRule.onNodeWithContentDescription("轻棋局网页内容").assertIsDisplayed()
        composeRule.onAllNodesWithText("连接公网生产环境").assertCountEquals(0)
    }
}
