package com.mdkdw1.mypdf

import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class PdfPageAdapter(
    private val pages: List<PageData>
) : RecyclerView.Adapter<PdfPageAdapter.ViewHolder>() {

    data class PageData(
        val index: Int,
        val bitmap: Bitmap,
        val widthPt: Float,
        val heightPt: Float
    )

    class ViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val iv = ImageView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_START
            setBackgroundColor(Color.BLACK)
        }
        return ViewHolder(iv)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val page = pages[position]
        holder.imageView.setImageBitmap(page.bitmap)
        holder.imageView.contentDescription = "Page ${page.index + 1}"
    }

    override fun getItemCount() = pages.size
}
