package com.petalert

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class WaypointPicturesAdapter(private val pictureFiles: List<File>) : RecyclerView.Adapter<WaypointPicturesAdapter.PictureViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PictureViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_picture, parent, false)
        return PictureViewHolder(view)
    }

    override fun onBindViewHolder(holder: PictureViewHolder, position: Int) {
        // Implement onBindViewHolder logic here
        Glide.with(holder.itemView.context).load(pictureFiles[position]).into(holder.picture)
    }

    override fun getItemCount(): Int {
        return pictureFiles.size
    }

    inner class PictureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Implement ViewHolder logic here
        val picture: ImageView =itemView.findViewById(R.id.imageViewPicture)
    }
}
