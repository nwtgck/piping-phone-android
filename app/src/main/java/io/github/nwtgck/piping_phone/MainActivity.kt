package io.github.nwtgck.piping_phone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.AudioTrack.MODE_STREAM
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import permissions.dispatcher.*
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import kotlin.math.max


// Create a RecordAudio
// (Play command: play -t raw -r 44100 -e signed -b 16 -c 1 hoge2.rawfile) (command from: https://stackoverflow.com/a/25362384/2885946)
// (from: https://qiita.com/ino-shin/items/214dba25f49fa098402f)
fun createAudioRecord(): AudioRecord {
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
        audioBufferSizeInByte
    )

    return audioRecord
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

@RuntimePermissions
class MainActivity : AppCompatActivity()  {

    companion object {
        val CONNECT_ID_PREF_KEY      = "CONNECT_ID_PREF_KEY"
        val PEER_CONNECT_ID_PREF_KEY = "PEER_CONNECT_ID_PREF_KEY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Get shared preferences
        val pref = getSharedPreferences("pref", Context.MODE_PRIVATE)

        // Connect ID edit
        var connectIdEdit: EditText = findViewById(R.id.connect_id)
        // Peer's connect ID edit
        var peerConnectIdEdit: EditText = findViewById(R.id.connection_id)
        // Connect button
        var connectButton: Button = findViewById(R.id.connect_button)
        // Save connect-IDs button
        val saveConnectIdsButton: Button = findViewById(R.id.save_connect_ids_button)

        // Set random connect ID
        connectIdEdit.setText(
            pref.getString(
                CONNECT_ID_PREF_KEY,
                randomConnectId(3)
            )
        )

        // Set peer's connect ID
        peerConnectIdEdit.setText(
            pref.getString(PEER_CONNECT_ID_PREF_KEY, "")
        )

        // On connect button clicked
        connectButton.setOnClickListener {
            recordSendReceivePlayAudioWithPermissionCheck()
        }

        saveConnectIdsButton.setOnClickListener {
            // Save connect IDs
            pref.edit()
                .putString(CONNECT_ID_PREF_KEY, connectIdEdit.text.toString())
                .putString(PEER_CONNECT_ID_PREF_KEY, peerConnectIdEdit.text.toString())
                .apply()
            Toast.makeText(applicationContext, "Connect IDs saved", Toast.LENGTH_LONG).show()
        }
    }

    @NeedsPermission(Manifest.permission.RECORD_AUDIO)
    fun recordSendReceivePlayAudio() {
        // Server URL edit
        var serverUrlEdit: EditText = findViewById(R.id.server_url)
        // Connect ID edit
        var connectIdEdit: EditText = findViewById(R.id.connect_id)
        // Peer's connect ID edit
        var peerConnectIdEdit: EditText = findViewById(R.id.connection_id)


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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // NOTE: delegate the permission handling to generated function
        onRequestPermissionsResult(requestCode, grantResults)
    }

    @OnShowRationale(Manifest.permission.RECORD_AUDIO)
    fun showRationaleForRecordAudio(request: PermissionRequest) {
        showRationaleDialog("A phone needs to send your voice to your receiver.", request)
    }

    @OnPermissionDenied(Manifest.permission.RECORD_AUDIO)
    fun onRecordAudioDenied() {
        Toast.makeText(this, "record audio denied", Toast.LENGTH_SHORT).show()
    }

    @OnNeverAskAgain(Manifest.permission.RECORD_AUDIO)
    fun onRecordAudioNeverAskAgain() {
        Toast.makeText(this, "never_askagain", Toast.LENGTH_SHORT).show()
    }

    private fun recordAndSendSound(serverUrl: String, connectId: String, peerConnectId: String) {
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

                    val audioRecord = createAudioRecord()
                    Log.i("audioRecord.bufferSizeInFrames", audioRecord.bufferSizeInFrames.toString())

                    con.setChunkedStreamingMode(audioRecord.bufferSizeInFrames / 2)
                    con.connect()

                    // Start recording
                    audioRecord.startRecording()

                    val os = con.outputStream

                    val bytes = ByteArray(audioRecord.bufferSizeInFrames)
                    var read = 0
                    while ({read = audioRecord.read(bytes, 0, bytes.size); read}() > 0) {
                        Log.i("Before Record", "before record")
                        os.write(bytes, 0, read)
                        Log.i("Record WRITE", "write ${read} bytes")
                        Thread.yield()
                    }
                    Log.i("Record finish", "POST finished")
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
                        Log.i("READ PLAY", "read ${totalRead} bytes")
                        if(read < 0) break
                        Thread.yield()
                    }
                    Log.i("READ PLAY finish", "GET finished")

                } catch (e: Throwable) {
                    Log.e("error message", "${e?.message}")
                } finally {
                    con?.disconnect()
                }
                return null
            }
        }
        getTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    // (base: https://github.com/permissions-dispatcher/PermissionsDispatcher/blob/95c7286ae4cb42cc6880d4d4031619ac1bacf03b/sample/src/main/java/permissions/dispatcher/sample/MainActivity.java#L107)
    private fun showRationaleDialog(messageResId: String, request: PermissionRequest) {
        AlertDialog.Builder(this)
            .setPositiveButton("Allow", { dialog, which -> request.proceed() })
            .setNegativeButton("Deny", { dialog, which -> request.cancel() })
            .setCancelable(false)
            .setMessage(messageResId)
            .show()
    }
}
