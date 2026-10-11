package com.mdkdw1.mypdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class PdfEditPageAdapter(
    private val pages: List<PdfEditSession.PageInfo>,
    private val thumbnails: Map<Int, Bitmap?>,
    private val onPageClick: (Int) -> Unit,
    private val onPageLongClick: (Int) -> Unit
) : RecyclerView.Adapter<PdfEditPageAdapter.ViewHolder>() {

    class ViewHolder(val card: CardView) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val ctx = parent.context

        val card = CardView(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(6, 6, 6, 6)
            }
            radius = 12f
            cardElevation = 4f
            setCardBackgroundColor(Color.WHITE)
            useCompatPadding = true
            preventCornerOverlap = false
        }

        val container = FrameLayout(ctx)

        val iv = ImageView(ctx).apply {
            id = 1
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(iv)

        val badge = TextView(ctx).apply {
            id = 2
            setTextColor(Color.WHITE)
            textSize = 11f
            setBackgroundColor(0xFFFF6B9D.toInt())
            setPadding(20, 8, 20, 8)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(8, 8, 0, 0)
            }
        }
        container.addView(badge)

        val rotateBadge = TextView(ctx).apply {
            id = 3
            setTextColor(Color.WHITE)
            textSize = 10f
            setBackgroundColor(0xCC9C6BFF.toInt())
            setPadding(16, 6, 16, 6)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, 8, 8, 0)
            }
            visibility = View.GONE
        }
        container.addView(rotateBadge)

        card.addView(container)
        return ViewHolder(card)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val page = pages[position]
        val container = holder.card.getChildAt(0) as FrameLayout
        val iv = container.findViewById<ImageView>(1)
        val badge = container.findViewById<TextView>(2)
        val rotateBadge = container.findViewById<TextView>(3)

        val thumb = thumbnails[page.originalIndex]
        if (thumb != null) {
            iv.setImageBitmap(thumb)
        } else {
            iv.setImageDrawable(null)
            iv.setBackgroundColor(Color.parseColor("#EEEEEE"))
        }

        badge.text = "${position + 1}"

        if (page.rotation != 0) {
            rotateBadge.text = "↻${page.rotation}°"
            rotateBadge.visibility = View.VISIBLE
        } else {
            rotateBadge.visibility = View.GONE
        }

        holder.card.setOnClickListener { onPageClick(page.originalIndex) }
        holder.card.setOnLongClickListener {
            onPageLongClick(page.originalIndex)
            true
        }
    }

    override fun getItemCount() = pages.size
}

// ═══════════════ 아이콘 그리드 메뉴 어댑터 ═══════════════

data class MenuItem(
    val icon: String,
    val label: String,
    val id: Int,
    val isDanger: Boolean = false
)

class MenuGridAdapter(
    private val ctx: Context,
    private val items: List<MenuItem>
) : BaseAdapter() {

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val v = convertView ?: LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 20, 12, 20)
            gravity = Gravity.CENTER
        }
        (v as LinearLayout).removeAllViews()

        val item = items[position]

        val icon = TextView(ctx).apply {
            text = item.icon
            textSize = 28f
            gravity = Gravity.CENTER
        }
        val label = TextView(ctx).apply {
            text = item.label
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(if (item.isDanger) 0xFFEF5350.toInt() else 0xFFFFFFFF.toInt())
            setPadding(0, 6, 0, 0)
            maxLines = 2
        }

        v.addView(icon)
        v.addView(label)
        return v
    }
}
