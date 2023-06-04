package com.petalert.ui.map

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import androidx.fragment.app.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.petalert.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.petalert.AddWaypointActivity
import com.petalert.WaypointModel

class MapsFragment : Fragment(), GoogleMap.InfoWindowAdapter{

    private lateinit var centerMarker: Marker
    private lateinit var googleMap: GoogleMap
    private lateinit var addButton: Button
    private lateinit var cancelButton:Button
    private val locationRequestCode = 1000
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val waypointRef = FirebaseDatabase.getInstance().getReference("waypoints")
    private val geoFire = GeoFire(FirebaseDatabase.getInstance().getReference("geofire_waypoints"))
    private var lastQueryLocation = GeoLocation(0.0,0.0)
    private val markers: MutableMap<String, Marker> = mutableMapOf()
    private val marker_models: MutableMap<Marker, WaypointModel> = mutableMapOf()

    @SuppressLint("MissingPermission")
    private val callback = OnMapReadyCallback { map ->
        googleMap=map
        googleMap.setInfoWindowAdapter(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        if(!checkNoLocationPermissions()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: android.location.Location? ->
                val currentLocation =
                    if (location != null) LatLng(location.latitude, location.longitude)
                    else LatLng(44.417805, 26.098794)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 13f))
            }
        }
        googleMap.setOnCameraMoveListener {
            val lat = googleMap.cameraPosition.target.latitude
            val lon = googleMap.cameraPosition.target.longitude
            if(GeoFireUtils.getDistanceBetween(lastQueryLocation,GeoLocation(lat,lon)) >= 5600 )
            {
                println(GeoFireUtils.getDistanceBetween(lastQueryLocation,GeoLocation(lat,lon)))
                lastQueryLocation = GeoLocation(lat, lon)
                geoFire.queryAtLocation(lastQueryLocation, 1005.6).addGeoQueryEventListener(
                    object : GeoQueryEventListener {
                        override fun onKeyEntered(key: String?, location: GeoLocation?) {
                            println("key entered")
                            waypointRef.child(key!!).get().addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val dataSnapshot = task.result
                                    val waypointModel = dataSnapshot.getValue(WaypointModel::class.java)

                                    // Access the data in the WaypointModel object
                                    if (waypointModel != null) {
                                        val marker = googleMap.addMarker(MarkerOptions()
                                            .position(LatLng(waypointModel.lat!!,waypointModel.lon!!)))
                                        println("added marker")
                                        markers[key!!] = marker!!
                                        marker_models[marker]=waypointModel
                                    }
                                } else {
                                    task.exception!!.printStackTrace()
                                }
                            }
                        }

                        override fun onKeyExited(key: String?) {
                            val marker = markers.remove(key)
                            marker?.remove()
                        }

                        override fun onKeyMoved(key: String?, location: GeoLocation?) {
                        }

                        override fun onGeoQueryReady() {

                        }

                        override fun onGeoQueryError(error: DatabaseError?) {
                        }
                    }
                )
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val rootView = inflater.inflate(R.layout.fragment_maps, container, false)
        addButton = rootView.findViewById<Button>(R.id.addWaypointButton)
        addButton.setOnClickListener(startAddWaypoint)
        cancelButton=rootView.findViewById<Button>(R.id.cancelAddWaypointButton)
        cancelButton.setOnClickListener(stopAddWaypoint)
        return rootView
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (checkNoLocationPermissions()) {
            // Request location permission
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                locationRequestCode
            )
        } else {
            // Permissions already granted, proceed with map initialization
            val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
            mapFragment?.getMapAsync(callback)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            locationRequestCode -> {
                val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
                mapFragment?.getMapAsync(callback)
            }
        }
    }
    private val createWaypoint: View.OnClickListener = View.OnClickListener {
        val target = googleMap.cameraPosition.target

        val intent = Intent(requireContext(), AddWaypointActivity::class.java)
        intent.putExtra("latitude", target.latitude)
        intent.putExtra("longitude", target.longitude)
        startActivity(intent)
    }
    private val startAddWaypoint : View.OnClickListener = View.OnClickListener {
        val centerLatLng = googleMap.cameraPosition.target
        centerMarker = googleMap.addMarker(MarkerOptions().position(centerLatLng).title("Map Center"))!!
        googleMap.setOnCameraMoveListener {
            val centerLatLng = googleMap.cameraPosition.target
            centerMarker?.remove() // Remove the previous center marker if it exists
            centerMarker =
                googleMap.addMarker(MarkerOptions().position(centerLatLng).title("Map Center"))!!
        }

        cancelButton.visibility=View.VISIBLE
        addButton.setOnClickListener(createWaypoint)
    }
    private val stopAddWaypoint : View.OnClickListener = View.OnClickListener {
        centerMarker?.remove() // Remove the previous center marker if it exists
        googleMap.setOnCameraMoveListener {
            val lat = googleMap.cameraPosition.target.latitude
            val lon = googleMap.cameraPosition.target.longitude
            println("got to the if")
            if(GeoFireUtils.getDistanceBetween(lastQueryLocation,GeoLocation(lat,lon))>0.6)
            {
                println("queried")
                lastQueryLocation = GeoLocation(lat, lon)
                geoFire.queryAtLocation(lastQueryLocation, 0.6).addGeoQueryEventListener(
                    object : GeoQueryEventListener {
                        override fun onKeyEntered(key: String?, location: GeoLocation?) {
                            waypointRef.child(key!!).get().addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val dataSnapshot = task.result
                                    val waypointModel = dataSnapshot.getValue(WaypointModel::class.java)

                                    // Access the data in the WaypointModel object
                                    if (waypointModel != null) {
                                        val marker = googleMap.addMarker(MarkerOptions()
                                            .position(LatLng(waypointModel.lat!!,waypointModel.lon!!))
                                            .title(waypointModel.description))
                                        println("added marker")
                                        markers[key!!] = marker!!
                                    }
                                } else {
                                    task.exception!!.printStackTrace()
                                }
                            }
                        }

                        override fun onKeyExited(key: String?) {
                            val marker = markers.remove(key)
                            marker?.remove()
                            marker_models.remove(marker)
                        }

                        override fun onKeyMoved(key: String?, location: GeoLocation?) {
                        }

                        override fun onGeoQueryReady() {

                        }

                        override fun onGeoQueryError(error: DatabaseError?) {
                        }
                    }
                )
            }
        }
        cancelButton.visibility=View.INVISIBLE
        addButton.setOnClickListener(startAddWaypoint)
    }

    private fun checkNoLocationPermissions() : Boolean{
        val notFine = ActivityCompat.checkSelfPermission(requireContext(),
            android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        val notGross =  ActivityCompat.checkSelfPermission(requireContext(),
            android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        return notFine and notGross
    }
    override fun getInfoWindow(marker: Marker): View? {
    return null
    }

    override fun getInfoContents(marker: Marker): View {
        val waypointInfoView = layoutInflater.inflate(R.layout.marker_detail_view, null)

        val infoWindowDescription = waypointInfoView.findViewById<TextView>(R.id.infoWindowDescription)
        val infoWindowPhoneNumber = waypointInfoView.findViewById<TextView>(R.id.infoWindowPhoneNumber)
        val model = marker_models[marker]
        infoWindowDescription.text=model!!.description
        infoWindowPhoneNumber.text=model.phone

        // Retrieve the waypoint data from the marker's tag
        // Retrieve the key from the markers MutableMap

        return waypointInfoView
    }


}