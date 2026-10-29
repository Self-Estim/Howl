# Enhanced Activity waveform implementation

Target: Coyote 3, on contribution branch `codex/enhanced-activity-waveforms`.

## Evidence and scope

The supplied research document is background material, not a specification or
instructions to replace this Android application with Python. Its explanation of
X-Toys' internal DSP, cubic interpolation, gain management, and BLE scheduling is
speculative. No source code or packet captures establishing those claims were
provided. Enhanced must be described as an experimental Howl implementation,
not a reproduction of X-Toys or a verified improvement in perceived quality.

Howl already generates pulses at 40 Hz in `PlayerService`, using a monotonic
deadline and an overrun guard. `BaseOutput` batches four pulses for Coyote 3.
`BluetoothHandler` serializes GATT operations and drops streaming writes when
busy. `CyclicalWave` already supports Hermite interpolation. Frequency-dependent
amplitude calibration and the Coyote's BF balance settings already exist.

The manufacturer's current [V3 protocol](https://github.com/dungeonlab-open/dglab-bluetooth-protocol/blob/main/coyote/v3/README.md)
specifies one 20-byte B0 packet per **100 ms**, containing four **25 ms** samples
per channel. It does not prescribe sending a B0 packet every 25 ms. Hardware
frequency/intensity balance settings persist; this feature must not change them
automatically. [X-Toys' public guide](https://guide.xtoys.app/getting-started/using-patterns.html)
describes patterns but does not establish the DSP claims in the research paper.

## First implementation

1. Add a saved Classic / Enhanced selector to Activity, defaulting to Classic.
2. Apply bounded, causal transition conditioning to Activity's normalized pulse
   envelope and frequency. Smooth upward amplitude transitions, allow immediate
   reductions and silence, and keep channels independent. Use elapsed simulation
   time so playback speed changes remain meaningful. Do not introduce spline
   overshoot, look-ahead, amplification, or a different BLE packet cadence.
3. Reset conditioning when activities, mode, or playback sessions change. Bypass
   calibration activities so calibration remains repeatable. Explain the
   experimental behavior in the interface and project documentation.
4. Verify Classic identity, numerical bounds, immediate silence/reductions,
   independent channels, reset behavior, different time steps, invalid values,
   and long-running generated sequences with deterministic JVM tests.
5. Build an APK and run Android unit tests using an isolated local toolchain.
   Review the final diff and commit author/committer identities before pushing
   only this contribution branch through origin's dedicated SSH configuration.

## Follow-up evidence

The user's X-Toys public reference patterns are: Penelope, Lilith's Punishment,
On the Edge of the World, Estim pleasure pattern, wowstereohmmm.freightgod69,
Kiss of Venus (Extended), Path to Nirvana, and Algea. These are comparison targets,
not bundled assets. Pattern names alone do not establish their format or signal
conversion method. Inspect their metadata and conversion settings before
attributing the perceived difference to interpolation or transport.

Exact X-Toys matching requires a named pattern and settings or a controlled BLE
capture. Compare equal power, frequency range, balance parameters, and electrode
setup, changing only one variable per trial. Record B0/BF bytes and timestamps;
check amplitude envelopes, frequency trajectories, sample timing, packet loss,
and stop/reconnect behavior. Strip device addresses and other personal data from
captures before sharing them. Hardware feel, radio timing, screen-off behavior,
and reconnect behavior cannot be validated from JVM tests.

The multimedia/AI, video player, packet-catalog, and Python application proposals
in the research document are separate future projects outside this first change.

## Build environment

Initial inspection found no available Java executable or Android SDK. Toolchain
downloads belong in the ignored `.waveform-tools` directory; do not modify global
PATH, global Git settings, SSH configuration, or credentials. The user authorized
project-local toolchain provisioning and Android SDK license acceptance. Preserve
the repository's existing
Gradle and dependency versions unless a verified compatibility issue requires a
separately documented change.

## Prototype behavior

The selector is saved as `activity_waveform_mode` using the existing preferences
database. Classic returns the original pulse unchanged. Enhanced uses causal
exponential conditioning with a 50 ms amplitude attack time constant and a 35 ms
frequency time constant; a step approaches 95% in approximately three time
constants. Falling amplitude and silence pass through immediately. A channel
retunes while silent before its next attack. Channels are processed independently.
These are initial engineering choices to evaluate, not measured X-Toys settings.

Conditioning occurs in Activity before channel swapping, output tweaks,
calibration, frequency mapping, and existing Coyote power limits. Thus it affects
all outputs playing Activity, including Activity recordings. It does not affect
Player files, Generator, Manual, or any of the three calibration activities.
Selecting a method during Activity playback pauses it; press Play to compare.
New playback sessions, activity replacements/restarts, and mode changes reset
the conditioning state. The BLE implementation and BF settings are unchanged.

There is no claim of exact X-Toys parity, improved radio stability, or proven
subjective quality. Hardware comparison remains necessary before promoting
Enhanced to a default or extending it to file playback.

## Verification

The debug APK build and all 16 JVM tests passed (15 new waveform tests and the
existing example test). The new tests include 100,000 deterministic randomized
pulses, exact Classic passthrough, calibration bypass, channel isolation,
immediate silence and reductions, elapsed-time consistency, reset behavior,
invalid input recovery, and numerical bounds. A final offline build also passed.
No dependency versions or Bluetooth code were changed. Existing compiler and
Gradle deprecation warnings remain outside this feature's scope.

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk` and is
not committed. No connected-device, emulator UI, or physical waveform comparison
has been performed. In particular, passing these tests does not establish a
subjective improvement over Classic or equivalence to the named X-Toys patterns.

To repeat the build with the provisioned local tools from the repository root:

```powershell
$env:JAVA_HOME = (Get-ChildItem .waveform-tools/java -Directory | Select-Object -First 1).FullName
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.waveform-tools/gradle-home'
$env:ANDROID_HOME = Join-Path (Get-Location) '.waveform-tools/android-sdk'
$env:ANDROID_USER_HOME = Join-Path (Get-Location) '.waveform-tools/android-user'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --max-workers=2
```
