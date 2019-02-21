package io.github.nwtgck.piping_phone

import android.support.v7.app.AppCompatActivity
import android.os.Bundle

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.widget.Button
import android.widget.Toast
import kotlin.math.max


// Record audio
// (from: https://qiita.com/ino-shin/items/214dba25f49fa098402f)
fun recordAudio(callback: (ShortArray) -> Unit){
    // Sampling rate (Hz)
    val samplingRate = 44100

    // Frame rate (fps)
    val frameRate = 10

    // Audio data size in 1 frame
    val oneFrameDataCount = samplingRate / frameRate

    // Audio data byte size in 1 frame
    // (NOTE: Byte = 8 bit, Short = 16 bit)
    val oneFrameSizeInByte = oneFrameDataCount * 2

    // Audio data buffer size
    // (NOTE: This should be > oneFrameSizeInByte)
    // (NOTE: It is necessary to make it larger than the minimum value required by the device)
    val audioBufferSizeInByte =
        max(oneFrameSizeInByte * 10,
            android.media.AudioRecord.getMinBufferSize(samplingRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT))


    // Create audio record
    val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        samplingRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        audioBufferSizeInByte)

    audioRecord.positionNotificationPeriod = oneFrameDataCount

    audioRecord.notificationMarkerPosition = 40000

    val audioDataArray = ShortArray(oneFrameDataCount)


    audioRecord.setRecordPositionUpdateListener(object : AudioRecord.OnRecordPositionUpdateListener {
        // Process in each frame
        override fun onPeriodicNotification(recorder: AudioRecord) {
            recorder.read(audioDataArray, 0, oneFrameDataCount)
            Log.v("AudioRecord", "onPeriodicNotification size=${audioDataArray.size}")
            callback(audioDataArray)
        }

        override fun onMarkerReached(recorder: AudioRecord) {
            recorder.read(audioDataArray, 0, oneFrameDataCount)
            Log.v("AudioRecord", "onMarkerReached size=${audioDataArray.size}")
        }
    })

    // Start recording
    audioRecord.startRecording()
}


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Record button
        val recordButton = findViewById<Button>(R.id.record_button)

        // TODO: Remove
        var cnt = 0
        recordButton.setOnClickListener {
            recordAudio {
                // TODO: Remove
                cnt += 1
                // TODO: Remove
                // Print
                Toast.makeText(applicationContext, cnt.toString(), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
