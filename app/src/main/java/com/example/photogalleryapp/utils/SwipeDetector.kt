package com.example.photogalleryapp.utils

import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

class SwipeDetector(private val onLeftSwipe: () -> Unit, private val onRightSwipe: () -> Unit) : GestureDetector.SimpleOnGestureListener() {
    companion object {
        private const val MIN_SWIPE_DISTANCE_X = 100
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        e1 ?: return super.onFling(e1, e2, velocityX, velocityY)

        val deltaX = e1.x - e2.x
        val deltaXAbs = abs(deltaX)

        if (deltaXAbs >= MIN_SWIPE_DISTANCE_X) {
            if (deltaX > 0) {
                onLeftSwipe()
            } else {
                onRightSwipe()
            }
        }

        return true
    }
}
