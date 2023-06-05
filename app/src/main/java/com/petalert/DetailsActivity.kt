package com.petalert

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.hcaptcha.sdk.HCaptcha
import com.petalert.WaypointModel
import com.petalert.WaypointPicturesAdapter
import java.io.File

class DetailsActivity : AppCompatActivity() {
    private lateinit var waypointKey: String
    private val hCaptcha = HCaptcha.getClient(this)

    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var phoneTextView: TextView
    private lateinit var phoneTextLabel: TextView

    private lateinit var picturesRecyclerView: RecyclerView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        // Retrieve the waypoint key from the extras
        waypointKey = intent.getStringExtra("waypoint_key") ?: ""

        // Initialize the views
        latitudeTextView = findViewById(R.id.latitudeTextView)
        longitudeTextView = findViewById(R.id.longitudeTextView)
        descriptionTextView = findViewById(R.id.descriptionTextView)
        phoneTextView = findViewById(R.id.phoneTextView)
        picturesRecyclerView = findViewById(R.id.picturesRecyclerView)
        phoneTextLabel=findViewById(R.id.phoneTextLabel)

        // Query the Firebase database for the WaypointModel using the waypointKey
        val database: FirebaseDatabase = FirebaseDatabase.getInstance()
        val waypointRef = database.reference.child("waypoints").child(waypointKey)
        waypointRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                var waypointModel = dataSnapshot.getValue(WaypointModel::class.java)!!
                latitudeTextView.text = waypointModel.lat.toString()
                longitudeTextView.text = waypointModel.lon.toString()
                descriptionTextView.text = waypointModel.description
                phoneTextView.text = waypointModel.phone
                picturesRecyclerView.layoutManager = LinearLayoutManager(applicationContext, LinearLayoutManager.HORIZONTAL, false)
                var pictureFilesList : MutableList<File> = mutableListOf()
                var pictureUrlList : MutableList<String> = mutableListOf()
                val imageRef=FirebaseDatabase.getInstance().getReference("waypoint_images/${waypointKey}")
                val pictureListener = object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        for (childSnapshot in dataSnapshot.children) {
                            val imageUrl = childSnapshot.getValue(String::class.java)
                            imageUrl?.let {
                                pictureUrlList.add(it)
                                // Download the image file and add it to pictureFilesList
                                val fileName = it.substringAfterLast("/")
                                val localFile = File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)
                                val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(it)

                                storageRef.getFile(localFile).addOnSuccessListener {
                                    pictureFilesList.add(localFile)
                                    // Update the RecyclerView or perform any other required operations
                                    picturesRecyclerView.adapter = WaypointPicturesAdapter(pictureFilesList)
                                }
                            }
                        }
                    }
                    override fun onCancelled(databaseError: DatabaseError) {
                        // Handle any errors that occur while retrieving the images
                    }
                }
                imageRef.addListenerForSingleValueEvent(pictureListener)

            }
            override fun onCancelled(databaseError: DatabaseError) {
                // Handle any errors that occur while querying the database
            }
        })
        phoneTextLabel.setOnClickListener{
            hCaptcha.setup().verifyWithHCaptcha()
        }
        hCaptcha.addOnSuccessListener{
            phoneTextView.visibility=View.VISIBLE
            FirebaseDatabase.getInstance().getReference("phone_access")
                .child(waypointKey).push().setValue(FirebaseAuth.getInstance().uid)
        }
        val reportButton = findViewById<Button>(R.id.buttonReport)
        reportButton.setOnClickListener{
            database.reference.child("reports").child(waypointKey).push().setValue(FirebaseAuth.getInstance().uid)
        }

        val shareButton = findViewById<Button>(R.id.shareButton)
        shareButton.setOnClickListener{
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                val description : String = (descriptionTextView.text as String?)!!
                val phoneNumber : String  = (phoneTextView.text as String?)!!
                val message = "$description\n Phone number:\n $phoneNumber"
                putExtra(Intent.EXTRA_TEXT,message )
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)

        }
    }
}
