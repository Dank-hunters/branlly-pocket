package com.branlly.pocket.domain

import com.branlly.pocket.platform.android.actions.AppTarget
import com.branlly.pocket.platform.android.actions.GenericMediaAppAdapter
import com.branlly.pocket.platform.android.actions.GenericNavigationAdapter
import com.branlly.pocket.platform.android.actions.NavigationTarget
import com.branlly.pocket.platform.android.actions.WazeAdapter
import com.branlly.pocket.platform.android.actions.YouTubeMusicAdapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAdapterSelectionTest {
    @Test
    fun `unknown media application uses generic fallback`() {
        val target = AppTarget("example.unknown.player")
        assertFalse(YouTubeMusicAdapter().supports(target))
        assertTrue(GenericMediaAppAdapter().supports(target))
    }

    @Test
    fun `provider adapter does not affect generic navigation fallback`() {
        val unknown = NavigationTarget("example.unknown.navigation")
        assertFalse(WazeAdapter().supports(unknown))
        assertTrue(GenericNavigationAdapter().supports(unknown))
    }
}
