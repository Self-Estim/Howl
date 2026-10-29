package com.example.howl

import kotlinx.serialization.Serializable

@Serializable
data class Pulse(
    val ampA: Float = 0.0f,
    val ampB: Float = 0.0f,
    val freqA: Float = 0.0f,
    val freqB: Float = 0.0f
)
