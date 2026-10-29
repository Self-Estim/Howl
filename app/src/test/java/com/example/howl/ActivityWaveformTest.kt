package com.example.howl

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ActivityWaveformTest {
    private val target = Pulse(1f, 0.6f, 0.8f, 0.2f)

    @Test fun classicPassesTheOriginalPulseThroughExactly() {
        val conditioner = ActivityWaveformConditioner()
        repeat(20) { conditioner.process(target, 0.025) }
        assertSame(target, conditioner.process(target, 0.025, ActivityWaveformMode.CLASSIC))
        assertEquals(ActivityWaveformConditioner().process(target, 0.025),
            conditioner.process(target, 0.025))
    }

    @Test fun calibrationBypassesEnhancedAndClearsState() {
        val conditioner = ActivityWaveformConditioner()
        repeat(20) { conditioner.process(target, 0.025) }
        assertSame(target, conditioner.process(target, 0.025,
            ActivityWaveformMode.ENHANCED, calibration = true))
        assertEquals(ActivityWaveformConditioner().process(target, 0.025),
            conditioner.process(target, 0.025))
    }

    @Test fun firstZeroTimeTickIsSilentAtTargetFrequency() {
        assertEquals(target.copy(ampA = 0f, ampB = 0f),
            ActivityWaveformConditioner().process(target, 0.0))
    }

    @Test fun risingEnvelopeConvergesWithoutOvershoot() {
        val conditioner = ActivityWaveformConditioner()
        var previous = Pulse()
        repeat(40) {
            val next = conditioner.process(target, 0.025)
            assertTrue(next.ampA in previous.ampA..target.ampA)
            assertTrue(next.ampB in previous.ampB..target.ampB)
            previous = next
        }
        assertEquals(1f, previous.ampA, 0.00001f)
        assertEquals(0.6f, previous.ampB, 0.00001f)
    }

    @Test fun amplitudeReductionAndSilenceAreImmediate() {
        val conditioner = ActivityWaveformConditioner()
        repeat(20) { conditioner.process(target, 0.025) }
        assertEquals(0.1f, conditioner.process(target.copy(ampA = 0.1f), 0.025).ampA, 0f)
        val silent = conditioner.process(Pulse(), 0.0)
        assertEquals(Pulse(), silent)
    }

    @Test fun channelChangesDoNotAffectOtherChannel() {
        val left = ActivityWaveformConditioner()
        val right = ActivityWaveformConditioner()
        repeat(100) { index ->
            val baseline = left.process(target, 0.025)
            val changed = right.process(target.copy(ampA = (index % 2).toFloat(),
                freqA = (index % 3) / 2f), 0.025)
            assertEquals(baseline.ampB, changed.ampB, 0f)
            assertEquals(baseline.freqB, changed.freqB, 0f)
        }
    }

    @Test fun frequencyTransitionsStayBetweenEndpoints() {
        val conditioner = ActivityWaveformConditioner()
        repeat(20) { conditioner.process(target, 0.025) }
        val next = conditioner.process(target.copy(freqA = 0f, freqB = 1f), 0.025)
        assertTrue(next.freqA > 0f && next.freqA < 0.8f)
        assertTrue(next.freqB > 0.2f && next.freqB < 1f)
    }

    @Test fun silenceRetunesBeforeNextAttack() {
        val conditioner = ActivityWaveformConditioner()
        repeat(20) { conditioner.process(target, 0.025) }
        val silent = conditioner.process(target.copy(ampA = 0f, freqA = 0.1f), 0.025)
        assertEquals(0.1f, silent.freqA, 0f)
        val restarted = conditioner.process(target.copy(freqA = 0.4f), 0.025)
        assertEquals(0.4f, restarted.freqA, 0f)
        assertTrue(restarted.ampA < 0.5f)
    }

    @Test fun resetDiscardsPreviousEnvelopeAndFrequency() {
        val conditioner = ActivityWaveformConditioner()
        repeat(20) { conditioner.process(target, 0.025) }
        conditioner.reset()
        assertEquals(ActivityWaveformConditioner().process(target, 0.025),
            conditioner.process(target, 0.025))
    }

    @Test fun envelopeDependsOnElapsedTimeRatherThanSampleCount() {
        val fast = ActivityWaveformConditioner()
        val slow = ActivityWaveformConditioner()
        var fastPulse = Pulse()
        var slowPulse = Pulse()
        repeat(20) { fastPulse = fast.process(target, 0.0125) }
        repeat(10) { slowPulse = slow.process(target, 0.025) }
        assertEquals(slowPulse.ampA, fastPulse.ampA, 0.000001f)
        assertEquals(slowPulse.ampB, fastPulse.ampB, 0.000001f)
    }

    @Test fun invalidInputsFailSilentPerChannelAndRecover() {
        val conditioner = ActivityWaveformConditioner()
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(0f, conditioner.process(target.copy(ampA = invalid), 0.025).ampA, 0f)
            assertEquals(0f, conditioner.process(target.copy(freqB = invalid), 0.025).ampB, 0f)
        }
        assertTrue(conditioner.process(target, 0.025).ampA > 0f)
    }

    @Test fun invalidTimeFailsSilentAndDiscardsState() {
        val conditioner = ActivityWaveformConditioner()
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1)) {
            conditioner.process(target, 0.025)
            val result = conditioner.process(target, invalid)
            assertEquals(0f, result.ampA, 0f)
            assertEquals(0f, result.ampB, 0f)
            assertEquals(ActivityWaveformConditioner().process(target, 0.025),
                conditioner.process(target, 0.025))
        }
    }

    @Test fun delayedTickCannotSkipTheAttack() {
        val conditioner = ActivityWaveformConditioner()
        assertTrue(conditioner.process(target, 60.0).ampA < 0.9f)
    }

    @Test fun randomizedLongRunNeverAmplifiesOrLeavesNormalizedRange() {
        val random = Random(47)
        val conditioner = ActivityWaveformConditioner()
        repeat(100_000) { index ->
            if (index % 193 == 0) conditioner.reset()
            val input = Pulse(random.nextFloat(), random.nextFloat(),
                random.nextFloat(), random.nextFloat())
            val output = conditioner.process(input, random.nextDouble(0.0, 0.2))
            assertTrue(output.ampA in 0f..input.ampA)
            assertTrue(output.ampB in 0f..input.ampB)
            assertTrue(output.freqA in 0f..1f)
            assertTrue(output.freqB in 0f..1f)
        }
    }

    @Test fun outOfRangeValuesAreClamped() {
        val result = ActivityWaveformConditioner().process(Pulse(-1f, 2f, -5f, 6f), 0.025)
        assertEquals(0f, result.ampA, 0f)
        assertTrue(result.ampB in 0f..1f)
        assertEquals(0f, result.freqA, 0f)
        assertEquals(1f, result.freqB, 0f)
    }
}
