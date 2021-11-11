package com.example.kidsdrawingapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    //we need to draw on this view. so we ll add all variables and functions we need
    private var mDrawPath: CustomPath? = null  //CustomPath is a custom inner class
    private var mCanvasBitmap: Bitmap? = null
    private var mDrawPaint: Paint? = null
    private var mCanvasPaint: Paint? = null
    private var mBrushSize: Float = 0.0F
    private var color: Int = Color.BLACK
    private var canvas: Canvas? = null  //canvas on which we will draw on
    //or private lateinit canvas: Canvas
    private val mPaths = ArrayList<CustomPath>()
    private var mUndoPaths = ArrayList<CustomPath>()
//    private var motionTouchEventX = 0f
//    private var motionTouchEventY = 0f
    init {
        setUpDrawing()
    }

    private fun setUpDrawing() {
        mDrawPath = CustomPath(color, mBrushSize)
        mDrawPaint = Paint().apply {
            style = Paint.Style.STROKE
            color = this.color              //coz we just created mDrawPaint above this so we sure it is not null
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        mCanvasPaint = Paint(Paint.DITHER_FLAG)
        setBrushSize(10.0f)

    }

    //Because the view starts out with no size, the view's onSizeChanged() method is
    // also called after the Activity first creates and inflates it. This onSizeChanged()
    // method is therefore the ideal place to create and set up the view's canvas.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mCanvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(mCanvasBitmap!!)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(mCanvasBitmap!!, 0f, 0f,mCanvasPaint)
        for (path in mPaths){
            mDrawPaint!!.apply {        //because we want to be able to change brush thickness and color later
                strokeWidth = path.brushThickness
                color = path.color

            }
            canvas.drawPath(path, mDrawPaint!!)
        }
        if (!mDrawPath!!.isEmpty) {     //mDrawPath is of CustomPath class we make so we gotta make sure its not empty
            //mDrawPaint!!.strokeWidth = mDrawPath!!.brushThickness
            mDrawPaint!!.apply {
                strokeWidth = mDrawPath!!.brushThickness
                color = mDrawPath!!.color

            }
            canvas.drawPath(mDrawPath!!, mDrawPaint!!)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val motionTouchEventX = event?.x
        val motionTouchEventY = event?.y
        when(event?.action){
            MotionEvent.ACTION_DOWN -> {
                mDrawPath!!.color = color
                mDrawPath!!.brushThickness = mBrushSize
                mDrawPath!!.reset()
                if (motionTouchEventX != null && motionTouchEventY != null) {
                    mDrawPath!!.moveTo(motionTouchEventX, motionTouchEventY)

                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (motionTouchEventX != null && motionTouchEventY != null)
                    mDrawPath!!.lineTo(motionTouchEventX, motionTouchEventY)
            }
            MotionEvent.ACTION_UP -> {
                mPaths.add(mDrawPath!!)
                mDrawPath = CustomPath(color, mBrushSize)
                //mDrawPath.reset()
            }
            else ->
                return false
        }
        invalidate()
        return true
    }

    //not private coz we want to be able to use this function in other classes like Main Activity
    //applyDimension returns the complex floating point value mutliplied by appropriate metrics depending on its unit
    fun setBrushSize(newBrushSize: Float){
        mBrushSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newBrushSize, resources.displayMetrics)
        mDrawPaint?.strokeWidth = mBrushSize
    }

    fun setColor(newColor: String){
        color = newColor.toColorInt()
        mDrawPaint?.color = color
    }

//    private fun touchStart() {
//        mDrawPath?.reset()
//        mDrawPath?.moveTo(motionTouchEventX, motionTouchEventY)
//
//    }
//
//    private fun touchMove() {
//    }
//
//    private fun touchUp() {
//    }

    internal inner class CustomPath(var color: Int,
                                    var brushThickness: Float): Path() {
    }

    fun undo(){
        if (mPaths.isNotEmpty()){
            mUndoPaths.add(mPaths.removeAt(mPaths.size - 1))
            invalidate()
        }
    }

    fun redo(){
        mPaths.add(mUndoPaths[mUndoPaths.size - 1])
        invalidate()
    }

}