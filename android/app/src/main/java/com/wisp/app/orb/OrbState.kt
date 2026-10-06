package com.wisp.app.orb

/**
 * The three distinct states of Wisp's floating presence.
 * Reference: PRD.md Section 7.1.1 & DESIGN.md Section 6
 */
enum class OrbState {
    /**
     * Minimal, small, ambient presence at screen edge with gentle breathing pulse.
     */
    IDLE,

    /**
     * Active voice listening state with animated waveform / pulsing glow.
     */
    COMPACT,

    /**
     * Morphed translucent glassmorphism panel showing task snapshot and controls.
     */
    EXPANDED
}
