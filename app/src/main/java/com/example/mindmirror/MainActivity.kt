package com.example.mindmirror

import com.package.name.R
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Rect
import java.io.IOException
import kotlin.math.log10

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var db: DetectionDbHelper
    private var mediaRecorder: MediaRecorder? = null
    private var recording = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.CAMERA] == true
        if (granted) startCamera()
        else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlayView)
        db = DetectionDbHelper(this)

        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        findViewById<Button>(R.id.btnAudio).setOnClickListener {
            if (!recording) startRecording() else stopRecording()
        }

        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        } else startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val realTimeOpts = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()

            val detector = FaceDetection.getClient(realTimeOpts)

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy: ImageProxy ->
                processImageProxy(detector, imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis)
            } catch (e: Exception) {
                Log.e("MindMirror", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(detector: com.google.mlkit.vision.face.FaceDetector, imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val boxes = mutableListOf<Pair<Rect, String>>()
                    if (faces.isNotEmpty()) {
                        val f = faces[0]
                        val smile = f.smilingProbability ?: 0f
                        val label = when {
                            smile > 0.7f -> "Happy"
                            smile > 0.3f -> "Neutral"
                            else -> "Sad/Neutral"
                        }
                        val bounds = f.boundingBox
                        boxes.add(Pair(bounds, "$label (${String.format("%.2f", smile)})"))
                        // save detection to DB
                        lifecycleScope.launch(Dispatchers.IO) {
                            db.insert(System.currentTimeMillis(), label, smile)
                        }
                    }
                    overlay.setBoxes(boxes)
                }
                .addOnFailureListener { e ->
                    Log.e("MindMirror", "Face detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun startRecording() {
        mediaRecorder = MediaRecorder()
        val mr = mediaRecorder ?: return
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            val outFile = "${'$'}{cacheDir.absolutePath}/tmp_audio.3gp"
            mr.setOutputFile(outFile)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mr.prepare()
            mr.start()
            recording = true
            findViewById<Button>(R.id.btnAudio).text = "Stop Audio"
            // periodically read amplitude and show simple stress proxy after 3 seconds
            Thread {
                try {
                    Thread.sleep(3000)
                    val amp = mr.maxAmplitude // getMaxAmplitude after some audio is recorded
                    val dbLevel = if (amp > 0) 20 * log10(amp.toDouble()) else 0.0
                    val stressLabel = if (dbLevel > 60) "High stress (loud)" else if (dbLevel > 40) "Moderate" else "Calm"
                    runOnUiThread {
                        Toast.makeText(this, "Audio check: ${'$'}stressLabel (amp=${'$'}amp)", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Audio start failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        recording = false
        findViewById<Button>(R.id.btnAudio).text = "Start Audio"
    }
}
