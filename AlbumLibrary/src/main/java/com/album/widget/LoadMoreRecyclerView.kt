package com.album.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * @author y
 * @create 2019/2/27
 */
class LoadMoreRecyclerView : RecyclerView {

    private var lastVisibleItemPosition: Int = 0
    private lateinit var loadingListener: LoadMoreListener

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    fun setLoadingListener(loadingListener: LoadMoreListener) {
        this.loadingListener = loadingListener
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        val layoutManager = layoutManager as GridLayoutManager
        lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        val visibleItemCount = layoutManager?.childCount ?: 0
        val totalItemCount = layoutManager?.itemCount ?: 0
        if (visibleItemCount > 0 && state == RecyclerView.SCROLL_STATE_IDLE && lastVisibleItemPosition == totalItemCount - 1) {
            loadingListener.onLoadMore()
        }
    }

    interface LoadMoreListener {
        fun onLoadMore()
    }
}