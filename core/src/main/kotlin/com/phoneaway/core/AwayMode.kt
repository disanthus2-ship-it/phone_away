package com.phoneaway.core

/**
 * How strictly the app verifies that the phone is actually being left alone.
 *
 * Modes are ordered easiest to hardest, and the multiplier rises with how hard
 * the mode is to cheat. [HONOR] cannot be verified at all, so it pays the least;
 * [STILLNESS] requires the phone to be locked *and* set down, so it pays the most.
 */
enum class AwayMode(
    val id: String,
    val displayName: String,
    val tagline: String,
    /** Applied to base points. Higher = harder to fake. */
    val multiplier: Double,
    /** What ends a session early in this mode, in plain language. */
    val breaksOn: String,
) {
    HONOR(
        id = "honor",
        displayName = "Honor",
        tagline = "Nothing is enforced. Just you and your word.",
        multiplier = 1.0,
        breaksOn = "Only you can end it, by tapping \"I gave up\".",
    ),

    APP_OPEN(
        id = "app_open",
        displayName = "App Open",
        tagline = "Leave this screen up and don't touch anything else.",
        multiplier = 1.25,
        breaksOn = "Switching apps or going to the home screen.",
    ),

    SCREEN_LOCKED(
        id = "screen_locked",
        displayName = "Screen Locked",
        tagline = "Lock the phone and leave it locked. Peeking at the clock is fine.",
        multiplier = 1.75,
        breaksOn = "Unlocking the phone.",
    ),

    STILLNESS(
        id = "stillness",
        displayName = "Hands Off",
        tagline = "Locked and put down. Pockets don't count.",
        multiplier = 2.5,
        breaksOn = "Unlocking the phone, or picking it up at all.",
    );

    /** True when the mode needs the screen to be off before the clock starts. */
    val requiresLockedScreen: Boolean
        get() = this == SCREEN_LOCKED || this == STILLNESS

    /** True when the mode watches the accelerometer. */
    val requiresStillness: Boolean
        get() = this == STILLNESS

    companion object {
        fun fromId(id: String): AwayMode = entries.firstOrNull { it.id == id } ?: HONOR
    }
}
