package com.example.howl

import kotlin.math.exp
import kotlin.math.min

enum class ActivityWaveformMode(val displayName: String) {
    CLASSIC("Classic"),
    ENHANCED("Enhanced")
}

/**
 * Causal envelope conditioning for generated Activity pulses, before output calibration.
 * This is an experimental Howl algorithm, not an implementation of X-Toys' internals.
 *
 * Amplitude rises have a 50 ms time constant; reductions (including silence) are immediate.
 * Frequency uses a 35 ms time constant. Exponential steps are bounded by their endpoints,
 * so there is no spline overshoot or look-ahead. No channel power or device gain is changed.
 * Owned by the player loop; call [reset] on that same thread at session/mode/activity changes.
 */
class ActivityWaveformConditioner {
    private var previous: Pulse? = null

    fun reset() {
        previous = null
    }

    fun process(
        target: Pulse,
        deltaTime: Double,
        mode: ActivityWaveformMode = ActivityWaveformMode.ENHANCED,
        calibration: Boolean = false
    ): Pulse {
        if (mode == ActivityWaveformMode.CLASSIC || calibration) {
            reset()
            return target
        }
        // Invalid frequency data also silences its channel rather than choosing an arbitrary
        // active frequency. Invalid amplitudes fail to zero independently on each channel.
        val clean = Pulse(
            ampA = if (target.freqA.isFinite()) unit(target.ampA) else 0f,
            ampB = if (target.freqB.isFinite()) unit(target.ampB) else 0f,
            freqA = unit(target.freqA),
            freqB = unit(target.freqB)
        )
        if (!deltaTime.isFinite() || deltaTime < 0.0) {
            reset()
            return clean.copy(ampA = 0f, ampB = 0f)
        }

        val old = previous ?: clean.copy(ampA = 0f, ampB = 0f)
        // The first source tick has zero elapsed time. Begin the attack on the next tick.
        // Cap stalls at 100 ms so a delayed loop cannot jump directly to full amplitude.
        val dt = deltaTime.coerceAtMost(0.1)
        val attack = (1.0 - exp(-dt / 0.050)).toFloat()
        val frequency = (1.0 - exp(-dt / 0.035)).toFloat()
        val result = Pulse(
            ampA = min(clean.ampA, step(old.ampA, clean.ampA, attack)),
            ampB = min(clean.ampB, step(old.ampB, clean.ampB, attack)),
            // Retune a silent channel immediately; no old frequency bleeds into its next attack.
            freqA = if (old.ampA == 0f || clean.ampA == 0f) clean.freqA
                else step(old.freqA, clean.freqA, frequency),
            freqB = if (old.ampB == 0f || clean.ampB == 0f) clean.freqB
                else step(old.freqB, clean.freqB, frequency)
        )
        previous = result
        return result
    }

    private fun unit(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

    private fun step(from: Float, to: Float, amount: Float): Float =
        (from + (to - from) * amount).coerceIn(minOf(from, to), maxOf(from, to))
}
