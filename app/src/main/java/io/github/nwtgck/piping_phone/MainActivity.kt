package io.github.nwtgck.piping_phone

import android.support.v7.app.AppCompatActivity
import android.os.Bundle

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.AsyncTask
import android.util.Log
import android.widget.Button
import android.widget.Toast
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max


// Record audio
// (Play command: play -t raw -r 44100 -e signed -b 16 -c 1 hoge2.rawfile) (command from: https://stackoverflow.com/a/25362384/2885946)
// (from: https://qiita.com/ino-shin/items/214dba25f49fa098402f)
fun recordAudio(callback: (ByteArray) -> Unit){
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

    val audioDataArray = ByteArray(oneFrameSizeInByte)


    audioRecord.setRecordPositionUpdateListener(object : AudioRecord.OnRecordPositionUpdateListener {
        // Process in each frame
        override fun onPeriodicNotification(recorder: AudioRecord) {
            recorder.read(audioDataArray, 0, audioDataArray.size)
            Log.i("AudioRecord", "read size=${audioDataArray.size}")
            callback(audioDataArray)
        }

        override fun onMarkerReached(recorder: AudioRecord) {
//            recorder.read(audioDataArray, 0, oneFrameDataCount)
            Log.i("AudioRecord", "mark size=${audioDataArray.size}")
        }
    })

    Log.i("in recordAudio","before start")
    // Start recording
    audioRecord.startRecording()
    Log.i("in recordAudio","after start")
}


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Record button
        val recordButton = findViewById<Button>(R.id.record_button)

        // TODO: Remove
        var cnt = 0

        val pOut = PipedOutputStream()
        val pIn = PipedInputStream(pOut)
        recordButton.setOnClickListener {
            recordAudio { audioArray ->
                cnt += 1
                Toast.makeText(applicationContext, cnt.toString(), Toast.LENGTH_LONG).show()
                pOut.write(audioArray)
            }

            // (from: https://qiita.com/furu8ma/items/0194a69a50aa62b8aa6c)
            val task = object : AsyncTask<Void, Void, Void>() {
                override fun doInBackground(vararg params: Void): Void? {
                    var con: HttpURLConnection? = null
                    try {
                        // TODO: hard code
                        val urlStr = "http://ppng.ml/hoge2"
                        val url = URL(urlStr)
                        con = url.openConnection() as HttpURLConnection
                        con.requestMethod = "POST"
                        con.instanceFollowRedirects = false
                        con.doInput = true
                        con.doOutput = true
                        con.allowUserInteraction = true
                        con.setChunkedStreamingMode(10)
                        con.connect()

                        val os = con.outputStream

                        val bytes = ByteArray(16)
                        while(pIn.read(bytes) > 0) {
                            os.write(bytes)
                        }

                    } catch (e: InterruptedException) {
                        Log.i("error", e.message)
                    } finally {
                        con?.disconnect()
                    }
                    return null
                }
            }
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
    }
}
