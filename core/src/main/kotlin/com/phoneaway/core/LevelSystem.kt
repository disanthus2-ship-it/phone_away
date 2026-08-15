package com.phoneaway.core

import kotlin.math.pow

data class LevelState(
    val level: Int,
    val title: String,
    val totalPoints: Int,
    /** Points earned since entering the current level. */
    val pointsIntoLevel: Int,
    /** Points the current level spans. Null at max level. */
    val pointsForLevel: Int?,
    /** 0f..1f through the current level. 1f at max level. */
    val progress: Float,
)

/**
 * Cumulative-points levelling on a gentle curve: early levels come fast so the
 * habit gets reinforced, later ones stretch out so the ladder doesn't run out.
 */
object LevelSystem {

    private const val BASE = 500.0
    private const val EXPONENT = 1.5

    private val titles = listOf(
        "Doomscroller",
        "Screen Sleeper",
        "Pocket Rookie",
        "Face-Down Apprentice",
        "Drawer Adept",
        "Airplane Mode Regular",
        "Silence Keeper",
        "Deep Focus Veteran",
        "Room Without a Phone",
        "Touch Grass Master",
        "Legend of the Offline",
    )

    val maxLevel: Int = titles.size

    /** Cumulative points required to reach [level]. Level 1 starts at zero. */
    fun pointsRequiredFor(level: Int): Int {
        require(level >= 1) { "level must be >= 1, was $level" }
        if (level == 1) return 0
        return (BASE * (level - 1).toDouble().pow(EXPONENT)).toInt()
    }

    fun levelFor(totalPoints: Int): Int {
        var level = 1
        while (level < maxLevel && totalPoints >= pointsRequiredFor(level + 1)) {
            level++
        }
        return level
    }

    fun titleFor(level: Int): String = titles[level.coerceIn(1, maxLevel) - 1]

    fun stateFor(totalPoints: Int): LevelState {
        val level = levelFor(totalPoints)
        val floor = pointsRequiredFor(level)

        if (level >= maxLevel) {
            return LevelState(
                level = level,
                title = titleFor(level),
                totalPoints = totalPoints,
                pointsIntoLevel = totalPoints - floor,
                pointsForLevel = null,
                progress = 1f,
            )
        }

        val ceiling = pointsRequiredFor(level + 1)
        val span = ceiling - floor
        val into = totalPoints - floor

        return LevelState(
            level = level,
            title = titleFor(level),
            totalPoints = totalPoints,
            pointsIntoLevel = into,
            pointsForLevel = span,
            progress = (into.toFloat() / span).coerceIn(0f, 1f),
        )
    }
}
