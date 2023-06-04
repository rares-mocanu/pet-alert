package com.petalert


data class WaypointModel (
    val userID: String?=null,
    val lat : Double?=null, val lon : Double?=null,
    var description : String?=null,
    var phone : String? =null){}