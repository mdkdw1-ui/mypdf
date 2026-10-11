package com.mdkdw1.mypdf

import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PdfEditPageAdapter(
    private val pages: List<PdfEditSession.PageInfo>,
    private val thumbnails: Map<Int, Bitmap?>,
    private val selected: MutableSet<Int>,
    private val selectionMode: Boolean,
    private val onPageClick: (Int) -> Unit,
    private val onPageLongClick: (Int) -> Unit
) : RecyclerView.Adapter<PdfEditPageAdapter.ViewHolder>() {

    class ViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val ctx = parent.context
        val container = FrameLayout(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(8, 8, 8, 8)
        }
        val card = FrameLayout(ctx).apply {
            setBackgroundColor(Color.WHITE)
        }
        val iv = ImageView(ctx).apply {
            id = 1
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(iv)
        val tvNum = TextView(ctx).apply {
            id = 2
            setTextColor(Color.WHITE)
            textSize = 12f
            setBackgroundColor(0xCC000000.toInt())
            setPadding(12, 6, 12, 6)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
        }
        card.addView(tvNum)
        val tvCheck = TextView(ctx).apply {
            id = 3
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(0xCCFF6B9D.toInt())
            setPadding(16, 8, 16, 8)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.END }
            visibility = View.GONE
        }
        card.addView(tvCheck)
        container.addView(card)
        return ViewHolder(container)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val page = pages[position]
        val card = holder.container.getChildAt(0) as FrameLayout
        val iv = card.findViewById<ImageView>(1)
        val tvNum = card.findViewById<TextView>(2)
        val tvCheck = card.findViewById<TextView>(3)

        val thumb = thumbnails[page.originalIndex]
        if (thumb != null) iv.setImageBitmap(thumb)
        else {
            iv.setImageDrawable(null)
            iv.setBackgroundColor(Color.parseColor("#EEEEEE"))
        }

        tvNum.text = if (page.rotation != 0) "${position + 1}  ↻${page.rotation}°"
                     else "${position + 1}"

        if (selectionMode) {
            if (selected.contains(page.originalIndex)) {
                tvCheck.text = "✓"
                tvCheck.visibility = View.VISIBLE
                card.setBackgroundColor(Color.parseColor("#88FF6B9D"))
            } else {
                tvCheck.visibility = View.GONE
                card.setBackgroundColor(Color.WHITE)
            }
        } else {
            tvCheck.visibility = View.GONE
            card.setBackgroundColor(Color.WHITE)
        }

        holder.container.setOnClickListener { onPageClick(page.originalIndex) }
        holder.container.setOnLongClickListener {
            onPageLongClick(page.originalIndex); true
        }
    }

    override fun getItemCount() = pages.size
}
