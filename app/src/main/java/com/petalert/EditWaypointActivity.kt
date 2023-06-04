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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.petalert.databinding.ActivityEditWaypointBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditWaypointActivity : AppCompatActivity() {
    private lateinit var waypointKey: String
    private lateinit var database: FirebaseDatabase
    private lateinit var binding : ActivityEditWaypointBinding
    private lateinit var waypointModel: WaypointModel

    private var pictureUrlList = mutableListOf<String>()
    private var pictureFilesList = mutableListOf<File>()
    private lateinit var currentPhotoPath: String





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
                    this@EditWaypointActivity,
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
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EditWaypointActivity.CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
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

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val CAMERA_REQUEST_CODE = 120
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditWaypointBinding.inflate(layoutInflater)

        setContentView(binding.root)
        val saveButton = binding.saveButton
        val editDescription = binding.editDescription
        val editPhone = binding.editPhone
        // Get the waypoint key from the intent extra
        waypointKey = intent.getStringExtra("waypointKey") ?: ""

        // Initialize Firebase database reference
        database = FirebaseDatabase.getInstance()
        this.binding.recyclerViewPictures.layoutManager= LinearLayoutManager(this.applicationContext)
        // Retrieve the pictures from the database
        val imageRef=FirebaseDatabase.getInstance().getReference("waypoint_images").child(waypointKey)
        val pictureListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (childSnapshot in dataSnapshot.children) {
                    val imageUrl = childSnapshot.getValue(String::class.java)
                    imageUrl?.let {
                        pictureUrlList.add(it)

                        // Download the image file and add it to pictureFilesList
                        val fileName = it.substringAfterLast("/")
                        val localFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)
                        val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(it)

                        storageRef.getFile(localFile).addOnSuccessListener {
                            pictureFilesList.add(localFile)
                            // Update the RecyclerView or perform any other required operations
                            this@EditWaypointActivity.binding.recyclerViewPictures.adapter = WaypointPicturesAdapter(pictureFilesList)
                        }.addOnFailureListener { exception ->
                            // Handle the failure to download the image file
                            exception.printStackTrace()
                        }
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle any errors that occur while retrieving the images
            }
        }

        imageRef.addListenerForSingleValueEvent(pictureListener)


        // Retrieve the WaypointModel from the database
        val waypointRef = database.reference.child("waypoints").child(waypointKey)
        waypointRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                waypointModel = dataSnapshot.getValue(WaypointModel::class.java)!!
                // Populate the EditText fields with the waypoint data
                editDescription.setText(waypointModel.description)
                editPhone.setText(waypointModel.phone)
            }
            override fun onCancelled(databaseError: DatabaseError) {
                // Handle any errors that occur
                // ...
            }
        })
        saveButton.setOnClickListener {
            // Update the waypoint model with the edited data
            waypointModel.description = editDescription.text.toString()
            waypointModel.phone = editPhone.text.toString()

            // Update the data in the database
            waypointRef.setValue(waypointModel)
            imageRef.removeValue()
            for(PictureUrl in pictureUrlList)
                imageRef.push().setValue(PictureUrl)

            // Finish the activity and go back to the previous screen
            finish()
        }
        val addPictureButton = binding.buttonAddPicture
        val removePictureButton = binding.buttonRemovePicture

        addPictureButton.setOnClickListener {
            // Check camera permission
            if (ContextCompat.checkSelfPermission(
                    this@EditWaypointActivity,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Permission granted, start the camera intent
                takePicture()
            } else {
                // Request camera permission
                ActivityCompat.requestPermissions(
                    this@EditWaypointActivity,
                    arrayOf(android.Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_REQUEST_CODE
                )
            }
        }

        removePictureButton.setOnClickListener {
            if(pictureFilesList.isNotEmpty()) {
                // Remove the last added picture from the list and Firebase Storage
                val storageRef =
                    FirebaseStorage.getInstance().getReference("images/${pictureUrlList.last()}")
                storageRef.delete()
                pictureFilesList.removeLast()
                pictureUrlList.removeLast()
                // Update the RecyclerView to reflect the changes
                this.binding.recyclerViewPictures.adapter =
                    WaypointPicturesAdapter(pictureFilesList)
            }
        }

    }

}

