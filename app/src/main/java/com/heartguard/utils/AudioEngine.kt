package com.heartguard.utils

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

object AudioEngine {
    private var recorder: AudioRecord? = null
    private var player: MediaPlayer? = null
    private var recordingFile: File? = null
    private var recordingThread: Thread? = null
    private val recordedBytes = AtomicLong(0L)

    @Volatile
    private var isRecording = false

    @Synchronized
    @Suppress("MissingPermission")
    fun startRecording(context: Context) {
        stopRecording()

        val outputFile = File(context.applicationContext.cacheDir, RECORDING_FILE_NAME)
        if (outputFile.exists()) {
            outputFile.delete()
        }

        recordingFile = outputFile

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
        )
        require(minBufferSize > 0) {
            "AudioRecord buffer size is invalid: $minBufferSize"
        }
        val bufferSize = maxOf(minBufferSize, SAMPLE_RATE / 10 * BYTES_PER_SAMPLE)
        val activeRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize,
        )
        if (activeRecorder.state != AudioRecord.STATE_INITIALIZED) {
            activeRecorder.release()
            throw IllegalStateException("AudioRecord is not initialized")
        }

        val wavOutput = RandomAccessFile(outputFile, "rw")
        wavOutput.setLength(0)
        writeWavHeader(wavOutput, audioDataLength = 0L)

        try {
            activeRecorder.startRecording()
        } catch (error: RuntimeException) {
            wavOutput.close()
            activeRecorder.release()
            throw error
        }

        recorder = activeRecorder
        recordedBytes.set(0L)
        isRecording = true
        recordingThread = Thread(
            {
                writeRecordingLoop(
                    activeRecorder = activeRecorder,
                    output = wavOutput,
                    bufferSize = bufferSize,
                )
            },
            "HeartGuardAudioRecorder",
        ).apply {
            start()
        }

        DebugLogger.d(TAG, "Recording started: ${outputFile.absolutePath}")
    }

    @Synchronized
    fun stopRecording(): File? {
        val activeRecorder = recorder ?: return recordingFile
        val outputFile = recordingFile

        try {
            isRecording = false
            runCatching {
                activeRecorder.stop()
            }.onFailure { error ->
                DebugLogger.w(TAG, "Failed to stop AudioRecord cleanly.", error)
            }
            runCatching {
                recordingThread?.join(RECORDING_THREAD_JOIN_TIMEOUT_MILLIS)
            }.onFailure { error ->
                DebugLogger.w(TAG, "Failed to wait for recording thread.", error)
            }
            outputFile?.let { file ->
                updateWavHeader(file, recordedBytes.get())
            }
            DebugLogger.d(TAG, "Recording stopped: ${outputFile?.absolutePath}, size=${outputFile?.length()}")
        } catch (error: RuntimeException) {
            DebugLogger.w(TAG, "Failed to stop AudioRecord cleanly.", error)
            outputFile?.delete()
            recordingFile = null
            return null
        } finally {
            activeRecorder.release()
            recorder = null
            recordingThread = null
            isRecording = false
        }

        return outputFile?.takeIf {
            it.exists() && it.length() > WAV_HEADER_SIZE + MIN_RECORDING_BYTES
        }
    }

    @Synchronized
    fun playAudio(
        fileUrlOrPath: String,
        volume: Float = 1.0f,
        onCompletion: () -> Unit,
    ) {
        stopPlaying()

        if (fileUrlOrPath.isBlank()) {
            DebugLogger.e(TAG, "Audio path/url is blank")
            onCompletion()
            return
        }

        val isRemoteUrl =
            fileUrlOrPath.startsWith("http://") || fileUrlOrPath.startsWith("https://")

        if (!isRemoteUrl) {
            val file = File(fileUrlOrPath)
            if (!file.exists()) {
                DebugLogger.e(TAG, "Audio file does not exist: $fileUrlOrPath")
                onCompletion()
                return
            }
        }

        player = MediaPlayer().apply {
            try {
                setDataSource(fileUrlOrPath)

                setOnPreparedListener { preparedPlayer ->
                    val safeVolume = volume.coerceIn(0f, 1f)
                    preparedPlayer.setVolume(safeVolume, safeVolume)
                    preparedPlayer.start()
                    DebugLogger.d(TAG, "Audio playback started: $fileUrlOrPath")
                }

                setOnCompletionListener {
                    DebugLogger.d(TAG, "Audio playback completed")
                    releasePlayer()
                    onCompletion()
                }

                setOnErrorListener { _, what, extra ->
                    DebugLogger.w(TAG, "MediaPlayer playback error: what=$what, extra=$extra")
                    releasePlayer()
                    onCompletion()
                    true
                }

                prepareAsync()
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Failed to set data source for: $fileUrlOrPath", e)
                releasePlayer()
                onCompletion()
            }
        }
    }

    @Synchronized
    fun stopPlaying() {
        player?.let { activePlayer ->
            try {
                if (activePlayer.isPlaying) {
                    activePlayer.stop()
                }
            } catch (error: IllegalStateException) {
                DebugLogger.w(TAG, "Failed to stop MediaPlayer cleanly.", error)
            } finally {
                activePlayer.release()
                player = null
            }
        }
    }

    @Synchronized
    fun setPlaybackVolume(volume: Float) {
        val safeVolume = volume.coerceIn(0f, 1f)
        player?.setVolume(safeVolume, safeVolume)
    }

    @Synchronized
    private fun releasePlayer() {
        player?.release()
        player = null
    }

    private fun writeRecordingLoop(
        activeRecorder: AudioRecord,
        output: RandomAccessFile,
        bufferSize: Int,
    ) {
        val buffer = ByteArray(bufferSize)
        try {
            while (isRecording) {
                val bytesRead = activeRecorder.read(buffer, 0, buffer.size)
                when {
                    bytesRead > 0 -> {
                        output.write(buffer, 0, bytesRead)
                        recordedBytes.addAndGet(bytesRead.toLong())
                    }

                    bytesRead < 0 -> {
                        DebugLogger.w(TAG, "AudioRecord read failed: $bytesRead")
                        break
                    }
                }
            }
        } catch (error: Exception) {
            DebugLogger.e(TAG, "Failed to write recording data.", error)
        } finally {
            runCatching {
                output.close()
            }
        }
    }

    private fun updateWavHeader(
        file: File,
        audioDataLength: Long,
    ) {
        if (!file.exists()) {
            return
        }
        RandomAccessFile(file, "rw").use { output ->
            writeWavHeader(output, audioDataLength)
        }
    }

    private fun writeWavHeader(
        output: RandomAccessFile,
        audioDataLength: Long,
    ) {
        val byteRate = SAMPLE_RATE * CHANNEL_COUNT * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNEL_COUNT * BITS_PER_SAMPLE / 8
        output.seek(0)
        output.writeAscii("RIFF")
        output.writeIntLE(audioDataLength + 36L)
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeIntLE(16)
        output.writeShortLE(WAV_PCM_FORMAT)
        output.writeShortLE(CHANNEL_COUNT)
        output.writeIntLE(SAMPLE_RATE)
        output.writeIntLE(byteRate)
        output.writeShortLE(blockAlign)
        output.writeShortLE(BITS_PER_SAMPLE)
        output.writeAscii("data")
        output.writeIntLE(audioDataLength)
    }

    private fun RandomAccessFile.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun RandomAccessFile.writeIntLE(value: Long) {
        write((value and 0xff).toInt())
        write(((value shr 8) and 0xff).toInt())
        write(((value shr 16) and 0xff).toInt())
        write(((value shr 24) and 0xff).toInt())
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        writeIntLE(value.toLong())
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
    }

    private const val TAG = "AudioEngine"
    private const val RECORDING_FILE_NAME = "temp_record.wav"
    private const val SAMPLE_RATE = 16_000
    private const val CHANNEL_COUNT = 1
    private const val BITS_PER_SAMPLE = 16
    private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
    private const val WAV_PCM_FORMAT = 1
    private const val WAV_HEADER_SIZE = 44L
    private const val MIN_RECORDING_BYTES = 1000L
    private const val RECORDING_THREAD_JOIN_TIMEOUT_MILLIS = 1500L
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
}
