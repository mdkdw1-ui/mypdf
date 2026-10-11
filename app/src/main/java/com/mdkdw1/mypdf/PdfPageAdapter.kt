package com.mdkdw1.mypdf

import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.chrisbanes.photoview.PhotoView

class PdfPageAdapter(
    private val pages: List<PageData>
) : RecyclerView.Adapter<PdfPageAdapter.ViewHolder>() {

    data class PageData(
        val index: Int,
        val bitmap: Bitmap,
        val widthPt: Float,
        val heightPt: Float
    )

    class ViewHolder(val photoView: PhotoView) : RecyclerView.ViewHolder(photoView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val pv = PhotoView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true
            // ⭐ 핀치 줌 범위
            minimumScale = 1.0f
            mediumScale = 2.0f
            maximumScale = 5.0f
            // 배경
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }
        return ViewHolder(pv)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val page = pages[position]
        holder.photoView.setImageBitmap(page.bitmap)
        holder.photoView.contentDescription = "Page ${page.index + 1}"
    }

    override fun getItemCount() = pages.size
}
