package io.github.nwtgck.piping_phone

import android.annotation.SuppressLint
import android.media.*
import android.support.v7.app.AppCompatActivity
import android.os.Bundle

import android.os.AsyncTask
import android.util.Log
import android.widget.Button
import android.widget.Toast
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import android.media.AudioTrack.MODE_STREAM
import android.widget.EditText
import java.io.BufferedInputStream
import java.security.SecureRandom


// Record audio
// (Play command: play -t raw -r 44100 -e signed -b 16 -c 1 hoge2.rawfile) (command from: https://stackoverflow.com/a/25362384/2885946)
// (from: https://qiita.com/ino-shin/items/214dba25f49fa098402f)
fun recordAudio(callback: (ByteArray, Int) -> Unit): Int {
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
        max(oneFrameSizeInByte,
            android.media.AudioRecord.getMinBufferSize(
                samplingRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        )

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
            val read = recorder.read(audioDataArray, 0, audioDataArray.size)
            Log.i("AudioRecord", "read size=${read}")
            callback(audioDataArray, read)
        }

        override fun onMarkerReached(recorder: AudioRecord) {
            recorder.read(audioDataArray, 0, oneFrameDataCount)
            Log.i("AudioRecord", "mark size=${audioDataArray.size}")
        }
    })

    Log.i("in recordAudio","before start")
    // Start recording
    audioRecord.startRecording()
    Log.i("in recordAudio","after start")

    // Return buffer size
    return audioBufferSizeInByte
}

fun getPath(fromId: String, toId: String): String {
    // TODO: Use SHA256
    return "phone/${fromId}-to-${toId}"
}

fun randomConnectId(stringLen: Int): String {
    // (from: https://www.baeldung.com/kotlin-random-alphanumeric-string)
    val random = SecureRandom()
    val charPool : List<Char> = listOf('a', 'b', 'c', 'd', 'e', 'f', 'h', 'i', 'j', 'k', 'm', 'n', 'p', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z')
    val randomString = (1..stringLen)
        .map { i -> random.nextInt(charPool.size) }
        .map(charPool::get)
        .joinToString("");
    return randomString
}

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Server URL edit
        val serverUrlEdit: EditText = findViewById(R.id.server_url)
        // Connect ID edit
        val connectIdEdit: EditText = findViewById(R.id.connect_id)
        // Peer's connect ID edit
        val peerConnectIdEdit: EditText = findViewById(R.id.connection_id)
        // Connect button
        val connectButton: Button = findViewById(R.id.connect_button)

        // Set random connect ID
        connectIdEdit.setText(randomConnectId(3))

        // On connect button clicked
        connectButton.setOnClickListener {
            // Get server base URL
            val serverUrl: String = serverUrlEdit.text.toString()
            // Get connect ID
            val connectId: String = connectIdEdit.text.toString()
            // Get peer's connect ID
            val peerConnectId: String = peerConnectIdEdit.text.toString()

            // Record and send sound
            recordAndSendSound(serverUrl, connectId, peerConnectId)

            // Receive and play sound
            receiveAndPlaySound(serverUrl, connectId, peerConnectId)
        }
    }

    private fun recordAndSendSound(serverUrl: String, connectId: String, peerConnectId: String) {
        // TODO: Remove
        var cnt = 0

        val pOut = PipedOutputStream()
        val pIn = PipedInputStream(pOut)

        val bufferSize = recordAudio { audioArray, read ->
            cnt += 1
            Toast.makeText(applicationContext, cnt.toString(), Toast.LENGTH_LONG).show()
            pOut.write(audioArray, 0, read)
        }
        Log.i("bufferSize record", bufferSize.toString())

        // (from: https://qiita.com/furu8ma/items/0194a69a50aa62b8aa6c)
        val task = @SuppressLint("StaticFieldLeak")
        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg params: Void): Void? {
                var con: HttpURLConnection? = null
                try {
                    // Create URL string
                    // TODO: Construct URL not to use "/" manually
                    val urlStr = "${serverUrl}/${getPath(connectId, peerConnectId)}"
                    Log.i("POST urlStr", urlStr)
                    val url = URL(urlStr)
                    val con = url.openConnection() as HttpURLConnection
                    con.requestMethod = "POST"
                    con.instanceFollowRedirects = false
                    con.doInput = true
                    con.doOutput = true
                    con.allowUserInteraction = true
                    con.setChunkedStreamingMode(128)
                    con.connect()

                    val os = con.outputStream

                    val bytes = ByteArray(512)
                    var read = 0
                    while ({read = pIn.read(bytes); read}() > 0) {
                        os.write(bytes, 0, read)
                        Thread.yield()
                    }

                } catch (e: Throwable) {
                    Log.i("error", e.message)
                } finally {
                    con?.disconnect()
                }
                return null
            }
        }
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    fun receiveAndPlaySound(serverUrl: String, connectId: String, peerConnectId: String) {
        // Buffer size for audio track
        val bufSize = AudioTrack.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        // (from: http://ytch.hatenablog.com/entry/2013/07/21/213130)
        val audioTrack = AudioTrack(
            AudioManager.STREAM_VOICE_CALL,
            44100, // TODO: hard code
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize,
            MODE_STREAM
        )
        Toast.makeText(applicationContext, "bufSize: "+bufSize.toString(), Toast.LENGTH_LONG).show()
        audioTrack.play()

        // (from: https://qiita.com/furu8ma/items/0194a69a50aa62b8aa6c)
        val getTask = @SuppressLint("StaticFieldLeak")
        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg params: Void): Void? {
                var con: HttpURLConnection? = null
                try {
                    // Create URL string
                    // TODO: Construct URL not to use "/" manually
                    val urlStr = "${serverUrl}/${getPath(peerConnectId, connectId)}"
                    Log.i("urlStr", urlStr)
                    val url = URL(urlStr)
                    con = url.openConnection() as HttpURLConnection
                    con.requestMethod = "GET"
                    con.instanceFollowRedirects = false
                    con.doInput = true
                    con.doOutput = false
                    con.setChunkedStreamingMode(256)
                    con.connect()

                    val iStream = BufferedInputStream(con.inputStream, bufSize)
                    val bytes = ByteArray(bufSize)
                    while(true) {
                        // Fill bytes until getting bytes.size except the last otherwise have noses
                        var read = 0
                        var totalRead = 0
                        do {
                            read = iStream.read(bytes, totalRead, bytes.size - totalRead)
                            if(read < 0) break
                            totalRead += read
                        } while (totalRead != bytes.size)
                        audioTrack.write(bytes, 0, totalRead)
                        if(read < 0) break
                        Thread.yield()
                    }

                } catch (e: Throwable) {
                    Log.i("error", e.message)
                } finally {
                    con?.disconnect()
                }
                return null
            }
        }
        getTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }
}
