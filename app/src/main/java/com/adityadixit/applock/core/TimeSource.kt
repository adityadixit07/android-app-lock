package com.adityadixit.applock.core

/**
 * Abstracts clocks so time-based security logic (grace periods, lockouts) is testable.
 *
 * - [elapsedRealtime] is monotonic and immune to the user changing the wall clock; use it for
 *   in-memory durations such as re-lock grace periods.
 * - [currentTimeMillis] survives reboots; use it for persisted deadlines such as lockouts.
 */
interface TimeSource {
    fun elapsedRealtime(): Long
    fun currentTimeMillis(): Long
}
