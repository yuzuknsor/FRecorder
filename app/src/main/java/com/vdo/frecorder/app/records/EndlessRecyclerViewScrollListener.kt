package com.vdo.frecorder.app.records

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

/** 列表滚动到底自动加载更多。Kotlin 化（原 EndlessRecyclerViewScrollListener.java）。 */
abstract class EndlessRecyclerViewScrollListener(layoutManager: RecyclerView.LayoutManager) : RecyclerView.OnScrollListener() {

	private var visibleThreshold = 5

	private var currentPage = 1

	private var previousTotalItemCount = 0

	private var loading = true

	private val mLayoutManager: RecyclerView.LayoutManager = layoutManager

	init {
		if (layoutManager is StaggeredGridLayoutManager) {
			visibleThreshold = visibleThreshold * layoutManager.spanCount
		} else if (layoutManager is GridLayoutManager) {
			visibleThreshold = visibleThreshold * layoutManager.spanCount
		}
	}

	private fun getLastVisibleItem(lastVisibleItemPositions: IntArray): Int {
		var maxSize = 0
		for (i in lastVisibleItemPositions.indices) {
			if (i == 0) {
				maxSize = lastVisibleItemPositions[i]
			} else if (lastVisibleItemPositions[i] > maxSize) {
				maxSize = lastVisibleItemPositions[i]
			}
		}
		return maxSize
	}

	//滚动期间每秒会触发多次，这里只做是否加载更多的判断
	override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
		var lastVisibleItemPosition = 0
		val totalItemCount = mLayoutManager.itemCount

		if (mLayoutManager is StaggeredGridLayoutManager) {
			val lastVisibleItemPositions = mLayoutManager.findLastVisibleItemPositions(null)
			//取最大值
			lastVisibleItemPosition = getLastVisibleItem(lastVisibleItemPositions)
		} else if (mLayoutManager is LinearLayoutManager) {
			lastVisibleItemPosition = mLayoutManager.findLastVisibleItemPosition()
		} else if (mLayoutManager is GridLayoutManager) {
			lastVisibleItemPosition = mLayoutManager.findLastVisibleItemPosition()
		}

		//总数变少且不为零视为列表被重置，恢复初始状态
		if (totalItemCount < previousTotalItemCount) {
			currentPage = STARTING_PAGE_INDEX
			previousTotalItemCount = totalItemCount
			if (totalItemCount == 0) {
				loading = true
			}
		}
		//正在加载时若总数增加，认为加载完成
		if (loading && totalItemCount > previousTotalItemCount + 1) {
			loading = false
			previousTotalItemCount = totalItemCount
		}

		//未在加载且越过了可见阈值，则请求加载更多
		if (!loading && lastVisibleItemPosition + visibleThreshold > totalItemCount && totalItemCount > visibleThreshold) {
			currentPage++
			onLoadMore(currentPage, totalItemCount)
			loading = true
		}
	}

	//按页加载更多数据
	abstract fun onLoadMore(page: Int, totalItemsCount: Int)

	//适配器数据整体变化时重置内部状态
	fun reset() {
		currentPage = 1
		previousTotalItemCount = 0
		loading = true
	}

	companion object {
		//起始页码
		private const val STARTING_PAGE_INDEX = 1
	}
}
