package com.projects.drawingapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var saved: Boolean = true
    private lateinit var drawingView: DrawingView
    private lateinit var brush: ImageButton
    private lateinit var gallery: ImageButton
    private lateinit var undo: ImageButton
    private lateinit var save: ImageButton
    private lateinit var share: ImageButton
    private lateinit var dialog: Dialog
    var result = ""

    private val openGalleryLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK && it.data != null) {
            val background = findViewById<ImageView>(R.id.iv_background)
            background.setImageURI(it.data?.data)
        }
    }
    private var tryingToPick = false

    private lateinit var imageButtonCurrentPaint: ImageButton
    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                if (isGranted) {
                    if (tryingToPick) {
                        Toast.makeText(this, "Opening gallery", Toast.LENGTH_SHORT).show()
                        val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        openGalleryLauncher.launch(pickIntent)
                    } else {
                        lifecycleScope.launch {
                            val frameLayout = findViewById<FrameLayout>(R.id.fl_drawing_view_layout)
                            saveBitmapFile(getBitmapFromView(frameLayout))
                        }
                    }
                } else {
                    if (permissionName == Manifest.permission.READ_EXTERNAL_STORAGE) {
                        Toast.makeText(this, "Permission Denied", Toast.LENGTH_LONG).show()
                    }
                }
                tryingToPick = false
            }

        }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)
        drawingView.setSizeForBrush(20f)

        brush = findViewById(R.id.brush)
        gallery = findViewById(R.id.gallery)
        undo = findViewById(R.id.undo)
        save = findViewById(R.id.save)
        share = findViewById(R.id.share)

        val linearLayout = findViewById<LinearLayout>(R.id.ll_paint_colors)

        drawingView.setOnTouchListener { v, event ->
            saved = false
            false
        }

        imageButtonCurrentPaint = linearLayout[1] as ImageButton
        imageButtonCurrentPaint.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )

        brush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        gallery.setOnClickListener {
            tryingToPick = true
            requestStoragePermission()
        }

        undo.setOnClickListener {
            drawingView.undoDrawing()
        }

        save.setOnClickListener {
            if (isReadStorageAllowed()) {
                showProgressDialog()
                lifecycleScope.launch {
                    val frameLayout = findViewById<FrameLayout>(R.id.fl_drawing_view_layout)
                    saveBitmapFile(getBitmapFromView(frameLayout))
                    runOnUiThread {
                        dialog.dismiss()
                    }
                }
            } else {
                requestStoragePermission()
            }
        }

        share.setOnClickListener {
            shareImage()
        }


    }

    private fun isReadStorageAllowed(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showRequestRationale("Permission Denied", "Permission was denied previously.")
        } else {
            requestPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private fun showRequestRationale(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        builder.create().show()
    }

    fun paintClicked(view: View) {
        if (view !== imageButtonCurrentPaint) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView.setColor(colorTag)
            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
            )
            imageButtonCurrentPaint.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )
            imageButtonCurrentPaint = view
        }
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)

        return returnedBitmap
    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size: ")
        val smallButton = brushDialog.findViewById<ImageButton>(R.id.small_brush)
        val mediumButton = brushDialog.findViewById<ImageButton>(R.id.medium_brush)
        val largeButton = brushDialog.findViewById<ImageButton>(R.id.large_brush)
        smallButton.setOnClickListener { drawingView.setSizeForBrush(10f); brushDialog.dismiss() }
        mediumButton.setOnClickListener { drawingView.setSizeForBrush(20f); brushDialog.dismiss() }
        largeButton.setOnClickListener { drawingView.setSizeForBrush(30f); brushDialog.dismiss() }
        brushDialog.show()
    }

    private suspend fun saveBitmapFile(bitMap: Bitmap?): String {

        withContext(Dispatchers.IO) {
            if (bitMap != null) {
                try {
                    val byte = ByteArrayOutputStream()
                    bitMap.compress(Bitmap.CompressFormat.PNG, 90, byte)
                    val file = if (result.isEmpty()) {
                        File(externalCacheDir?.toString() + File.separator + "KidsDrawingApp_" + System.currentTimeMillis() / 1000 + ".png")
                    } else {
                        File(result)
                    }
                    val fileOutputStream = FileOutputStream(file)
                    fileOutputStream.write(byte.toByteArray())
                    result = file.absolutePath
                    runOnUiThread {
                        if (result.isNotEmpty()) {
                            saved = true
                            Toast.makeText(this@MainActivity, result, Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Something went wrong", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showProgressDialog() {
        dialog = Dialog(this@MainActivity)
        dialog.setContentView(R.layout.dialog_progress)
        dialog.show()

    }

    private fun shareImage() {
        // MediaScannerConnection.scanFile() does not work in Android 10+ devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val shareIntent = Intent(Intent.ACTION_SEND)
            val uri = FileProvider.getUriForFile(this, "com.projects.drawingapp.fileprovider", File(result))
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share image using.."))
            return
        }
        MediaScannerConnection.scanFile(this, arrayOf(result), null) { path, uri ->
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share image using.."))
        }
    }

    override fun onBackPressed() {
        if (saved) {
            finish()
        } else {
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                dialog.setTitle("Save?")
                .setMessage("Do you want to save image?")
                .setPositiveButton("Wait") { d, which ->
                    d.dismiss()
                }
                .setNegativeButton("Don't Save"){
                    _, _->
                    finishAffinity()
                }
                }.show()

        }
    }

}