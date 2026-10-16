package com.adityadixit.applock.domain.auth

/** Single source of truth for credential constraints, shared by setup and unlock UIs. */
object CredentialRules {
    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 12
    const val MIN_PATTERN_CELLS = 4
    const val PATTERN_GRID_SIZE = 3

    fun isValidPin(pin: CharArray): Boolean =
        pin.size in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    fun isValidPattern(cells: List<Int>): Boolean =
        cells.size >= MIN_PATTERN_CELLS &&
            cells.distinct().size == cells.size &&
            cells.all { it in 0 until PATTERN_GRID_SIZE * PATTERN_GRID_SIZE }
}

/**
 * Patterns are hashed exactly like PINs: the ordered cell indices (0–8) become a digit string.
 * Returned as a CharArray so callers can wipe it after use.
 */
object PatternCodec {
    fun encode(cells: List<Int>): CharArray = CharArray(cells.size) { i -> '0' + cells[i] }
}
