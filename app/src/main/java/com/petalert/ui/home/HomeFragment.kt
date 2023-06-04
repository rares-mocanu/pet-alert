package com.petalert.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.petalert.LoginActivity
import com.petalert.MainActivity
import com.petalert.WaypointModel
import com.petalert.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root


        _binding!!.buttonSignOut.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(context, LoginActivity::class.java)
            startActivity(intent)
        }



        val myWaypointsRecyclerView= _binding!!.myWaypointsView
        myWaypointsRecyclerView.layoutManager = LinearLayoutManager(context)

        val listener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val waypointModels: MutableList<WaypointModel> = mutableListOf()

                // Iterate through the dataSnapshot to extract the WaypointModels
                for (snapshot in dataSnapshot.children) {
                    val waypointModel = snapshot.getValue(WaypointModel::class.java)
                    waypointModel?.let { waypointModels.add(it) }
                }
                myWaypointsRecyclerView.adapter=MyWaypointsAdapter(waypointModels)

            }
            override fun onCancelled(databaseError: DatabaseError) {
                // Handle any errors that occur
                // ...
            }
        }

        val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    // Define the query to retrieve the WaypointModels with a specific userID
        val query: Query = database.reference.child("waypoints")
            .orderByChild("userID")
            .equalTo(FirebaseAuth.getInstance().uid)

    // Attach the listener to the query
        query.addValueEventListener(listener)


        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}