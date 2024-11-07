package cm.test.ai_volume_tuner_back

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.google.mlkit.vision.face.FaceDetection
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {
    private lateinit var viewFinder: PreviewView
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize viewFinder and output directory
        viewFinder = findViewById(R.id.viewFinder)
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Request permissions if not already granted
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(outputDirectory, SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            })
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val audioFlags = AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Set up preview use case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            // Set up image capture use case
            imageCapture = ImageCapture.Builder().build()

            // Set up image analysis use case
            val imageAnalyzer = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(cameraExecutor, MyLabelAnalyzer { labels ->
                    var alarmVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (labels == 1) {
                        alarmVol = (alarmVol - 1).coerceAtLeast(0)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, alarmVol, audioFlags)
                    }
                })
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
/*
    private class MyLabelAnalyzer(private var listener: (Int) -> Unit) : ImageAnalysis.Analyzer {
        private val localModel = LocalModel.Builder().setAssetFilePath("mov4metadata.tflite").build()
        private val customImageLabelerOptions = CustomImageLabelerOptions.Builder(localModel)
            .setConfidenceThreshold(0.1f)
            .setMaxResultCount(5)
            .build()

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                val imageLabeler = ImageLabeling.getClient(customImageLabelerOptions)
                imageLabeler.process(image)
                    .addOnSuccessListener { labels ->
                        val labelCount = labels.count { it.confidence >= 0.5 }
                        listener(labelCount)
                    }
                    .addOnFailureListener { e -> Log.e(TAG, "Labeling error: $e") }
                    .addOnCompleteListener { imageProxy.close() }
            }
        }
    }
*/
    // My Classifier Image Labeling Model
    private class MyLabelAnalyzer(private var listener: (Int) -> Unit) : ImageAnalysis.Analyzer {

        val localModel = LocalModel.Builder()
            //.setAssetFilePath("lite-model_aiy_vision_classifier_food_V1_1.tflite")
            .setAssetFilePath("mov4metadata.tflite")
            //.setAssetFilePath("soudnAI.tflite")
            .build()

        val customImageLabelerOptions =
            CustomImageLabelerOptions.Builder(localModel)
                .setConfidenceThreshold(0.1f)
                .setMaxResultCount(5)
                .build()

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                // Pass image to food classifier model
                val imageLabeler = ImageLabeling.getClient(customImageLabelerOptions)
                imageLabeler.process(image)
                    .addOnSuccessListener { labels ->
                        // Task completed successfully
                        val ary = floatArrayOf(1.0f, 2.0f,3.0f)
                        var most = 0
                        var count = 1
                        for (label in labels) {
                            val text = label.text
                            val confidence = label.confidence
                            val index = label.index
                            //Log.d(TAG, "most confidence ans: $text, $confidence, $index")

                            if (label.text == "エラー") {
                                ary[0] = label.confidence
                            }

                            if (label.text == "不快") {
                                ary[1] = label.confidence
                            }

                            if (label.text == "普通") {
                                ary[2] = label.confidence
                            }
                            Log.d(TAG, "My Label: $text, $confidence, $index")
                            if(count == 1 && label.index == 1){
                                Log.d(TAG,"ary = $ary" )
                                most = 1
                            }
                            count++
                        }
                        listener(most)
                        Log.d(TAG, "end loop")
                    }

                    .addOnFailureListener { e ->
                        // Task failed with an exception
                        Log.e(TAG, "My Label: $e")
                    }
                    .addOnCompleteListener { results -> imageProxy.close() }
            }
        }

    }
}
