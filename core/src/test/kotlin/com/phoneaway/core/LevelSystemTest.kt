package com.phoneaway.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelSystemTest {

    @Test
    fun `everyone starts at level one`() {
        assertEquals(1, LevelSystem.levelFor(0))
        assertEquals(0, LevelSystem.pointsRequiredFor(1))
    }

    @Test
    fun `thresholds rise monotonically`() {
        for (level in 2..LevelSystem.maxLevel) {
            assertTrue(
                LevelSystem.pointsRequiredFor(level) > LevelSystem.pointsRequiredFor(level - 1),
                "level $level threshold must exceed level ${level - 1}",
            )
        }
    }

    @Test
    fun `crossing a threshold levels you up`() {
        val needed = LevelSystem.pointsRequiredFor(3)
        assertEquals(2, LevelSystem.levelFor(needed - 1))
        assertEquals(3, LevelSystem.levelFor(needed))
    }

    @Test
    fun `progress reports position within the current level`() {
        val floor = LevelSystem.pointsRequiredFor(2)
        val ceiling = LevelSystem.pointsRequiredFor(3)
        val state = LevelSystem.stateFor(floor + (ceiling - floor) / 2)

        assertEquals(2, state.level)
        assertEquals(0.5f, state.progress, 0.01f)
        assertEquals(ceiling - floor, state.pointsForLevel)
    }

    @Test
    fun `max level is terminal and fully progressed`() {
        val state = LevelSystem.stateFor(Int.MAX_VALUE / 2)
        assertEquals(LevelSystem.maxLevel, state.level)
        assertEquals(1f, state.progress)
        assertNull(state.pointsForLevel)
    }

    @Test
    fun `every level has a title`() {
        for (level in 1..LevelSystem.maxLevel) {
            assertTrue(LevelSystem.titleFor(level).isNotBlank())
        }
    }
}
