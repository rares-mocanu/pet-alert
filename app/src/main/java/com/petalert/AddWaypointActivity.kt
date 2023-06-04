package com.petalert

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.hcaptcha.sdk.HCaptcha
import com.petalert.databinding.ActivityAddWaypointBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class AddWaypointActivity : AppCompatActivity() {
    private val hCaptcha = HCaptcha.getClient(this)
    private val waypointRef = FirebaseDatabase.getInstance().getReference("waypoints")
    private val geoFire = GeoFire(FirebaseDatabase.getInstance().getReference("geofire_waypoints"))
    private val imageRef=FirebaseDatabase.getInstance().getReference("waypoint_images")
    private var pictureUrlList = mutableListOf<String>()
    private var pictureFilesList = mutableListOf<File>()

    private lateinit var binding : ActivityAddWaypointBinding
    private lateinit var currentPhotoPath : String



    private fun takePicture() {
        // Implement the logic to take a picture and upload it to Firebase Storage
        // Create a unique file name for the image
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "JPEG_${timeStamp}.jpg"

        // Create a Firebase Storage reference with the desired file name
        val storageRef = FirebaseStorage.getInstance().getReference("images/$fileName")

        // Start an intent to capture a picture using the camera
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val activityComponent = takePictureIntent.resolveActivity(packageManager)
        println(activityComponent)
        if (activityComponent != null) {
            println("entered if")
            // Create a file to temporarily store the captured image
            val photoFile: File? = try {
                createImageFile()
            } catch (ex: IOException) {
                // Handle the error if file creation fails
                ex.printStackTrace()
                null
            }
            // Continue if the file was created successfully
            photoFile?.let {
                val photoURI: Uri = FileProvider.getUriForFile(
                    this@AddWaypointActivity,
                    "com.petalert.fileprovider",
                    it
                )
                // Set the photo URI as the output for the camera intent
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                // Start the camera intent with a request code
                startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE)
            }
        }
    }
    // Create a file to save the captured image
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name with a timestamp
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            // Save the file path for future use if needed
            currentPhotoPath = absolutePath
        }
    }

    // Handle the result from the camera intent
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            // The picture was captured successfully
            // You can now upload the captured image to Firebase Storage
            val imageFile = File(currentPhotoPath)
            val imageUri = Uri.fromFile(imageFile)

            // Create a Firebase Storage reference with a unique file name
            val storageRef = FirebaseStorage.getInstance().getReference("images/${imageFile.name}")

            // Upload the image file to Firebase Storage
            storageRef.putFile(imageUri)
                .addOnSuccessListener {
                    // Image upload successful
                    val downloadUrlTask = storageRef.downloadUrl
                    downloadUrlTask.addOnSuccessListener { downloadUri ->
                        pictureUrlList.add(downloadUri.toString())
                        pictureFilesList.add(imageFile)
                        this.binding.recyclerViewPictures.adapter=WaypointPicturesAdapter(pictureFilesList)
                        this.binding.recyclerViewPictures.layoutManager= LinearLayoutManager(this.applicationContext)
                    // Add the image URL to your list or perform any other required operations
                    }
                }
                .addOnFailureListener { exception ->
                    // Image upload failed, handle the error
                    exception.printStackTrace()
                }
        } else {
            // Handle the case when the picture capture was canceled or failed
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE
            && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Camera permission granted, start the camera intent
            takePicture()
        } else {
            // Camera permission denied, show a message or handle the case accordingly
        }
    }


    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val CAMERA_REQUEST_CODE = 120
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddWaypointBinding.inflate(layoutInflater)
        setContentView(binding.root)



        val buttonSave=binding.buttonSave
        val buttonCancel=binding.buttonCancel
        val editTextDescription = binding.editTextDescription
        val editTextPhone = binding.editTextPhone
        val removePictureButton=binding.buttonRemovePicture
        val addPictureButton=binding.buttonAddPicture

        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)


        addPictureButton.setOnClickListener {
            // Check camera permission
            if (ContextCompat.checkSelfPermission(this@AddWaypointActivity,
                    android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start the camera intent
                takePicture()
            } else {
                // Request camera permission
                ActivityCompat.requestPermissions(this@AddWaypointActivity,
                    arrayOf(android.Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_REQUEST_CODE)
            }
        }
        removePictureButton.setOnClickListener {
            if(pictureFilesList.isNotEmpty())
            {
                val storageRef =
                    FirebaseStorage.getInstance().getReference("images/${pictureUrlList.last()}")
                storageRef.delete()
                pictureFilesList.removeLast()
                pictureUrlList.removeLast()
                this.binding.recyclerViewPictures.adapter =
                    WaypointPicturesAdapter(pictureFilesList)
            }
        }

        buttonSave.setOnClickListener {
            hCaptcha.setup().verifyWithHCaptcha()
        }
        buttonCancel.setOnClickListener {
            for(PictureUrl in pictureUrlList)
            {
                val storageRef = FirebaseStorage.getInstance().getReference("images/${pictureUrlList.last()}")
                storageRef.delete()
            }
            val intent = Intent(this@AddWaypointActivity, MainActivity::class.java)
            startActivity(intent)
        }
        hCaptcha
            .addOnSuccessListener { _ ->
                val waypoint = WaypointModel(
                    FirebaseAuth.getInstance().uid,
                    lat = latitude,
                    lon = longitude,
                    description = editTextDescription.text.toString(),
                    phone = editTextPhone.text.toString()
                )
                val waypointKey = waypointRef.push().key

                geoFire.setLocation(waypointKey, GeoLocation(latitude, longitude))
                waypointRef.child(waypointKey!!).setValue(waypoint)
                for(PictureUrl in pictureUrlList)
                    imageRef.child(waypointKey).push().setValue(PictureUrl)
                val intent = Intent(this@AddWaypointActivity, MainActivity::class.java)
                startActivity(intent)
            }

    }
}
