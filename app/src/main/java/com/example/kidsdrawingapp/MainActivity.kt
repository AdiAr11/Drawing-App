package com.example.kidsdrawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import com.example.kidsdrawingapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private var mCurrentSelectedPaintColor: ImageButton? = null
    private var customProgressDialog: Dialog? = null

    private lateinit var binding: ActivityMainBinding

    private val galleryImageResultListener = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()){ result ->
        if (result.resultCode == RESULT_OK && result.data != null){
            val data = result.data
            binding.backgroundImageView.setImageURI(data?.data)
        }
    }

    private val askForStorageAndOtherPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()){ permission ->
        permission.entries.forEach {
            val permissionName = it.key
            val isGranted = it.value
            if (permissionName == Manifest.permission.READ_EXTERNAL_STORAGE){
                if (isGranted){
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
                    galleryImageResultListener.launch(intent)
                }
                else{
                    Toast.makeText(this, "Storage Permission Denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mCurrentSelectedPaintColor = binding.paintColorsLinearLayout[1] as ImageButton
        //mCurrentSelectedPaintColor?.setImageResource(R.drawable.palette_selected)
        mCurrentSelectedPaintColor?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.palette_selected))

        binding.brushImageButton.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        binding.galleryImageButton.setOnClickListener {
            fetchImageFromGallery()
        }
        binding.undoImageButton.setOnClickListener {
            binding.drawingView.undo()
        }

        binding.redoImageButton.setOnClickListener {
            binding.drawingView.redo()
        }

        binding.saveImageButton.setOnClickListener {
            //checking for ReadStoragePermission because in newer android version it also provides write permission also
            val drawingViewContainerFrameLayout: FrameLayout = findViewById(R.id.drawingViewContainerFrameLayout)
            showProgressDialog()
            if (isReadStoragePermissionAllowed()){
                lifecycleScope.launch {
                    saveBitmapImage(bitmapFromView(drawingViewContainerFrameLayout))
                }
            }
        }
    }

    private fun fetchImageFromGallery() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)){
            showRationaleDialog()
        }
        else{
            askForStorageAndOtherPermissions.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private fun showRationaleDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Kids Drawing App requires storage access")
        builder.setMessage("Cannot fetch image because storage access is denied")
        builder.setPositiveButton("Cancel"){ dialogInterface, _ ->
            dialogInterface.dismiss()
        }
        builder.create().show()
    }

    private fun showBrushSizeChooserDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size: ")
        brushDialog.show()
        val smallBrushSizeButton = brushDialog.findViewById<ImageButton>(R.id.smallBrushImageButton)
        smallBrushSizeButton.setOnClickListener {
            binding.drawingView.setBrushSize(10.0f)
            brushDialog.dismiss()
        }
        brushDialog.findViewById<ImageButton>(R.id.mediumBrushImageButton).setOnClickListener {
            binding.drawingView.setBrushSize(20.0f)
            brushDialog.dismiss()
        }
        brushDialog.findViewById<ImageButton>(R.id.largeBrushImageButton).setOnClickListener {
            binding.drawingView.setBrushSize(30.0f)
            brushDialog.dismiss()
        }
    }

    fun colorPicker(view: View) {
        if(view != mCurrentSelectedPaintColor) {
            val imageButtonPressed = view as ImageButton
            val color = imageButtonPressed.tag.toString()
            binding.drawingView.setColor(color)
            imageButtonPressed.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.palette_selected
                )
            )
            mCurrentSelectedPaintColor?.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.palette_normal
                )
            )
            mCurrentSelectedPaintColor = imageButtonPressed
        }
    }

    private fun bitmapFromView(view: View): Bitmap{
        val bitmapToBeReturned = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmapToBeReturned)
        val backgroundImage = view.background
        if (backgroundImage != null){
            backgroundImage.draw(canvas)
        }
        else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return bitmapToBeReturned
    }

    private suspend fun saveBitmapImage(bitmap: Bitmap): String{
        var resultantPath = ""
        withContext(Dispatchers.IO){
            if (bitmap != null){
                try {
                    val bytes = ByteArrayOutputStream()//initial capacity is 32 bytes but it increases if necessary
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, bytes)
                    //compressed the image, so we need to make a file out of it
                    val fileName = "KidsDrawingApp_${System.currentTimeMillis()/1000}.png"
                    val locationOfFile = File(externalCacheDir?.absolutePath.toString() +
                            File.separator + fileName)
                    val fileOutputStream = FileOutputStream(locationOfFile)
                    fileOutputStream.write(bytes.toByteArray())
                    fileOutputStream.close()

                    resultantPath = locationOfFile.absolutePath

                    runOnUiThread {
                        cancelProgressDialog()
                        if (resultantPath.isNotEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "Image stored at $resultantPath",
                                Toast.LENGTH_LONG
                            ).show()
                            shareImage(resultantPath)
                        }
                        else
                            Toast.makeText(this@MainActivity, "Something went wrong...", Toast.LENGTH_SHORT).show()
                    }
                }catch (e: Exception){
                    resultantPath = ""
                    e.printStackTrace()
                }
            }
        }
        return resultantPath
    }

    private fun isReadStoragePermissionAllowed(): Boolean{
        val resultOfPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return resultOfPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun showProgressDialog(){
        customProgressDialog = Dialog(this@MainActivity)
        customProgressDialog?.setContentView(R.layout.custom_progress_dialog)
        customProgressDialog?.setCancelable(false)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog(){
        if (customProgressDialog != null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImage(fileLocation: String){
        MediaScannerConnection.scanFile(this, arrayOf(fileLocation), null) { path, uri ->
            val intent = Intent()
            intent.action = Intent.ACTION_SEND
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.type = "image/png"
            startActivity(Intent.createChooser(intent, "Share"))
        }
    }
}