package com.jipix.resonance.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Measures how loud a track actually is, so playback can level tracks against
 * each other instead of leaving the user riding the volume key between songs.
 *
 * ## What this is, and what it is not
 *
 * This is **not** EBU R128. Real R128 runs the signal through a K-weighting
 * filter pair — a shelf and a high-pass that approximate how the ear weighs
 * frequency — before measuring. That filter is what makes two tracks with very
 * different spectral balance compare fairly.
 *
 * What this does is mean-square energy with R128's *gating* scheme: 400 ms
 * blocks, an absolute floor to discard digital silence, then a relative gate
 * that drops everything far below the track's own average. The gating is the
 * part that matters most in practice — without it a song with a long quiet
 * intro reads as quiet overall and gets left far too loud.
 *
 * Skipping K-weighting costs accuracy when comparing, say, a bass-heavy
 * electronic track against a sparse acoustic one. It is a real limitation, not
 * a rounding error. It is also several hundred lines of filter design away, and
 * ungated RMS — the usual shortcut — is meaningfully worse than this.
 *
 * ## Why this decodes separately instead of listening to playback
 *
 * Reading the PCM as it plays would mean an `AudioProcessor` in the audio
 * pipeline, and any processor forces software decoding — exactly the trade this
 * project refuses to make by default. Decoding the file again on a background
 * thread runs far faster than real time, touches nothing in the playback path,
 * and leaves offload alone.
 */
object LoudnessAnalyzer {

    /**
     * Reference level everything is pulled down to. -14 is the streaming
     * convention (Spotify, YouTube, Tidal all sit within a decibel of it), which
     * makes it the level most listeners are already calibrated to.
     */
    const val TARGET_LUFS = -14.0

    /** Below this a block is digital silence and says nothing about loudness. */
    private const val ABSOLUTE_GATE_LUFS = -70.0

    /** R128's relative gate: blocks this far under the ungated mean drop out. */
    private const val RELATIVE_GATE_DB = -10.0

    private const val BLOCK_MS = 400

    /**
     * Analysing the whole of a long track buys very little. Three minutes covers
     * the body of nearly everything, and a track whose loudness changes
     * dramatically after that is unusual enough not to pay for on every file.
     */
    private const val MAX_ANALYSIS_US = 180_000_000L

    private const val DEQUEUE_TIMEOUT_US = 10_000L

    /**
     * @return the track's integrated level in LUFS-like units, or null when the
     *   file cannot be decoded — an unsupported codec, a missing file, a
     *   permission that went away.
     */
    suspend fun analyse(context: Context, uri: Uri): Double? = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor().apply { setDataSource(context, uri, null) }

            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return@withContext null

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val blockFrames = sampleRate * BLOCK_MS / 1000
            val blocks = collectBlockPowers(extractor, codec, channels, blockFrames)
            integrate(blocks)
        } catch (_: Exception) {
            // Every failure here is the same failure as far as the caller is
            // concerned: this file yielded no measurement. Retrying is the
            // caller's business, and there is nothing to salvage locally.
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }

    /** Mean square power per 400 ms block, in normalised [-1, 1] sample units. */
    private fun collectBlockPowers(
        extractor: MediaExtractor,
        codec: MediaCodec,
        channels: Int,
        blockFrames: Int,
    ): List<Double> {
        val info = MediaCodec.BufferInfo()
        val powers = ArrayList<Double>(512)

        var sumSquares = 0.0
        var framesInBlock = 0
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0 || extractor.sampleTime > MAX_ANALYSIS_US) {
                        codec.queueInputBuffer(
                            inputIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            if (outputIndex >= 0) {
                if (info.size > 0) {
                    val buffer = codec.getOutputBuffer(outputIndex)!!
                    val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                    while (shorts.hasRemaining()) {
                        // Channels are summed into one frame: loudness is a
                        // property of what reaches the listener, not of each
                        // speaker separately.
                        var frame = 0.0
                        for (channel in 0 until channels) {
                            if (!shorts.hasRemaining()) break
                            frame += shorts.get() / 32768.0
                        }
                        val mono = frame / channels
                        sumSquares += mono * mono
                        framesInBlock++

                        if (framesInBlock >= blockFrames) {
                            powers += sumSquares / framesInBlock
                            sumSquares = 0.0
                            framesInBlock = 0
                        }
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                // The decoder has nothing left and nothing more is coming.
                outputDone = true
            }
        }

        return powers
    }

    /**
     * R128's two-stage gate. The absolute pass throws away silence; the relative
     * pass then throws away everything well below the track's own average, which
     * is what stops a quiet intro from dragging the whole measurement down.
     */
    private fun integrate(blocks: List<Double>): Double? {
        if (blocks.isEmpty()) return null

        val aboveFloor = blocks.filter { it.toLufs() > ABSOLUTE_GATE_LUFS }
        if (aboveFloor.isEmpty()) return null

        val ungatedMean = aboveFloor.average()
        val relativeThreshold = ungatedMean.toLufs() + RELATIVE_GATE_DB
        val gated = aboveFloor.filter { it.toLufs() > relativeThreshold }

        val mean = if (gated.isEmpty()) ungatedMean else gated.average()
        return mean.toLufs()
    }

    /**
     * The gain to apply to reach [TARGET_LUFS], as a 0..1 multiplier.
     *
     * Only ever attenuates. `Player.volume` tops out at 1.0, so a track quieter
     * than the target simply plays at full scale — there is no headroom to lift
     * it into, and faking one by pulling everything else down further would just
     * make the whole app quiet.
     */
    fun gainFor(lufs: Double): Float {
        if (lufs <= TARGET_LUFS) return 1f
        val db = TARGET_LUFS - lufs
        return Math.pow(10.0, db / 20.0).toFloat().coerceIn(0.05f, 1f)
    }
}

private fun Double.toLufs(): Double =
    if (this <= 0.0) Double.NEGATIVE_INFINITY else 20 * log10(sqrt(this))
