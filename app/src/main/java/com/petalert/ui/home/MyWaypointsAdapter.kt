package com.petalert.ui.home

import android.content.Intent
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.geofire.GeoFire
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.petalert.EditWaypointActivity
import com.petalert.R
import com.petalert.WaypointModel
import com.petalert.WaypointPicturesAdapter
import java.io.File

class MyWaypointsAdapter(private var waypointList: MutableList<WaypointModel>) : RecyclerView.Adapter<MyWaypointsAdapter.WaypointViewHolder>() {

    inner class WaypointViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val latitudeTextView: TextView = itemView.findViewById(R.id.latitudeTextView)
        val longitudeTextView: TextView = itemView.findViewById(R.id.longitudeTextView)
        val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
        val phoneTextView: TextView = itemView.findViewById(R.id.phoneTextView)
        val editButton : Button = itemView.findViewById(R.id.editButton)
        val deleteButton : Button = itemView.findViewById(R.id.deleteButton)
        val picturesView : RecyclerView = itemView.findViewById(R.id.picturesRecyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WaypointViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_waypoint, parent, false)
        return WaypointViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: WaypointViewHolder, position: Int) {
        val currentWaypoint = waypointList[position]
        holder.latitudeTextView.text = currentWaypoint.lat.toString()
        holder.longitudeTextView.text = currentWaypoint.lon.toString()
        holder.descriptionTextView.text = currentWaypoint.description
        holder.phoneTextView.text = currentWaypoint.phone
        holder.picturesView.layoutManager= LinearLayoutManager(holder.itemView.context)
        val database: FirebaseDatabase = FirebaseDatabase.getInstance()
        val waypointRef = database.reference.child("waypoints")
        var waypointKey = ""
        var imageRef: DatabaseReference
        var pictureFilesList : MutableList<File> = mutableListOf()
        var pictureUrlList : MutableList<String> = mutableListOf()

        // Find the corresponding waypoint by matching its values
        val pictureListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (childSnapshot in dataSnapshot.children) {
                    val imageUrl = childSnapshot.getValue(String::class.java)
                    imageUrl?.let {
                        pictureUrlList.add(it)
                        // Download the image file and add it to pictureFilesList
                        val fileName = it.substringAfterLast("/")
                        val localFile = File(holder.itemView.context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)
                        val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(it)

                        storageRef.getFile(localFile).addOnSuccessListener {
                            pictureFilesList.add(localFile)
                            // Update the RecyclerView or perform any other required operations
                            holder.picturesView.adapter = WaypointPicturesAdapter(pictureFilesList)
                        }
                    }
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                // Handle any errors that occur while retrieving the images
            }
        }

        val query: Query = waypointRef
            .orderByChild("userID")
            .equalTo(currentWaypoint.userID)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (snapshot in dataSnapshot.children) {
                    val waypointSnapshot = snapshot.getValue(WaypointModel::class.java)
                    if (waypointSnapshot != null && waypointSnapshot == currentWaypoint) {
                        waypointKey=snapshot.key!!
                        imageRef=FirebaseDatabase.getInstance().getReference("waypoint_images").child(waypointKey)
                        imageRef.addListenerForSingleValueEvent(pictureListener)
                    }
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
            }
        })






        holder.deleteButton.setOnClickListener {
            val database: FirebaseDatabase = FirebaseDatabase.getInstance()
            val waypointRef = database.reference.child("waypoints")
            val geoFire = GeoFire(FirebaseDatabase.getInstance().getReference("geofire_waypoints"))
            val imageRef = database.reference.child("waypoint_images")
            // Find the corresponding waypoint by matching its values
            val query: Query = waypointRef
                .orderByChild("userID")
                .equalTo(currentWaypoint.userID)

            query.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    for (snapshot in dataSnapshot.children) {
                        val waypointSnapshot = snapshot.getValue(WaypointModel::class.java)
                        if (waypointSnapshot != null && waypointSnapshot == currentWaypoint) {
                            // Remove the waypoint from the database
                            snapshot.ref.removeValue()
                            geoFire.removeLocation(snapshot.key)
                            imageRef.child(snapshot.key!!).removeValue()
                            break
                        }
                    }
                }
                override fun onCancelled(databaseError: DatabaseError) {
                }
            })

        }
        holder.editButton.setOnClickListener {
            val intent = Intent(holder.itemView.context, EditWaypointActivity::class.java)
            intent.putExtra("waypointKey", waypointKey)
            holder.itemView.context.startActivity(intent)

        }
        }

    override fun getItemCount(): Int {
        return waypointList.size
    }
}
