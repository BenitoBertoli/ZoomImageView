/**
 * Copyright 2012 Benito Bertoli
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.benitobertoli.largeimagezoom;

import java.io.InputStream;

import android.app.ActionBar.LayoutParams;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * ZoomImageView is an Android™ custom View that supports pinch/double-tap
 * zooming and panning gestures. <br />
 * You can display very large images without having to worry about
 * java.lang.OutOfMemoryError.
 * 
 * @author Benito Bertoli
 * 
 */
public class ZoomImageView extends View {
	/** Default state of image. */
	static final int STATE_NONE = 0;

	/** Image is being dragged. */
	static final int STATE_DRAG = 1;

	/** Image is being zoomed. */
	static final int STATE_ZOOM = 2;

	/**
	 * The state of the image can be {@link #STATE_NONE}, {@link #STATE_DRAG} or
	 * {@link #STATE_ZOOM}.
	 */
	private int mState = STATE_NONE;

	/** Resource Id of the image to be displayed. */
	private int mImageId;

	/** InputStream used to open the image. */
	private InputStream mInputImage;

	/** Drawable object used to display the image. */
	private Drawable mDrawable;

	/** Height of image. */
	private int mImageHeight;

	/** Width of image. */
	private int mImageWidth;

	/** Height of this View. */
	private int mViewHeight;

	/** Width of this View. */
	private int mViewWidth;

	/** First point when dragging. */
	private PointF mPointLast = new PointF();

	/** Last point when dragging. */
	private PointF mPointStart = new PointF();

	/** Current scale of image. */
	private float mScale;

	/** Scale required to fit the image to the View. */
	private float mScaleFit;

	/** Maximum allowed scale. */
	private float mScaleMax = 3f;

	/** Minimum allowed scale. */
	private float mScaleMin = 1f;

	/** Left bound of {@link #mDrawable}. */
	private int mLeft = 0;

	/** Top bound of {@link #mDrawable}. */
	private int mTop = 0;

	/** Right bound of {@link #mDrawable}. */
	private int mRight = 0;

	/** Bottom bound of {@link #mDrawable}. */
	private int mBottom = 0;

	/** Gesture detector for detecting scale gestures. */
	private ScaleGestureDetector mScaleDetector;

	/** Gesture detector for detecting double taps. */
	private GestureDetector mDoubleTapDetector;

	/**
	 * Constructor.
	 * 
	 * @param context
	 */
	public ZoomImageView(Context context) {
		super(context);
		init(context);

	}

	/**
	 * Constructor.
	 * 
	 * @param context
	 * @param attrs
	 */
	public ZoomImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		TypedArray a = getContext().obtainStyledAttributes(attrs,
				R.styleable.ZoomImageView);
		mImageId = a.getResourceId(R.styleable.ZoomImageView_android_src,
				R.drawable.ic_launcher);
		init(context);
	}

	/**
	 * Some initializations.
	 * 
	 * @param context
	 */
	private void init(Context context) {
		mInputImage = getResources().openRawResource(mImageId);
		mDrawable = Drawable.createFromStream(mInputImage, "zoom_image");
		BitmapFactory.Options bounds = new BitmapFactory.Options();
		bounds.inJustDecodeBounds = true;
		BitmapFactory.decodeResource(getResources(), mImageId, bounds);
		if (bounds.outWidth == -1) {
			// TODO: Handle Error
		}
		mImageWidth = bounds.outWidth;
		mImageHeight = bounds.outHeight;
		mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
		mDoubleTapDetector = new GestureDetector(context,
				new DoubleTapListener());
		setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				mDoubleTapDetector.onTouchEvent(event);
				mScaleDetector.onTouchEvent(event);
				PointF pointCurr = new PointF(event.getX(), event.getY());

				switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					mPointLast.set(event.getX(), event.getY());
					mPointStart.set(mPointLast);
					mState = STATE_DRAG;
					break;

				case MotionEvent.ACTION_MOVE:
					if (mState == STATE_DRAG) {
						float deltaX = pointCurr.x - mPointLast.x;
						float deltaY = pointCurr.y - mPointLast.y;
						mLeft += deltaX;
						mTop += deltaY;
						mPointLast.set(pointCurr.x, pointCurr.y);
					}
					break;

				case MotionEvent.ACTION_UP:
					mState = STATE_NONE;
					break;

				case MotionEvent.ACTION_POINTER_UP:
					mState = STATE_NONE;
					break;
				}

				invalidate();
				return true;
			}
		});
	}

	/**
	 * Calculate {@link #mRight} and {@link #mBottom}. <br />
	 * If there are empty spaces around the image, update {@link #mLeft} and
	 * {@link #mTop} and recalculate {@link #mRight} and {@link #mBottom}. <br />
	 * Draw the image with the calculated bounds.
	 */
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		mRight = (int) (mScale * mImageWidth + mLeft);
		mBottom = (int) (mScale * mImageHeight + mTop);

		if (mImageWidth * mScale <= mViewWidth) {
			mLeft = (int) ((mViewWidth - mImageWidth * mScale) / 2);
		} else if (mLeft > 0) {
			mLeft = 0;
		} else if (mRight < mViewWidth) {
			mLeft += mViewWidth - mRight;
		}

		if (mImageHeight * mScale <= mViewHeight) {
			mTop = (int) ((mViewHeight - mImageHeight * mScale) / 2);
		} else if (mTop > 0) {
			mTop = 0;
		} else if (mBottom < mViewHeight) {
			mTop += mViewHeight - mBottom;
		}

		mRight = (int) (mScale * mImageWidth + mLeft);
		mBottom = (int) (mScale * mImageHeight + mTop);

		mDrawable.setBounds(mLeft, mTop, mRight, mBottom);
		mDrawable.draw(canvas);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		mViewWidth = getLayoutParams().width;
		mViewHeight = getLayoutParams().height;
		if (mViewWidth == LayoutParams.MATCH_PARENT) {
			mViewWidth = MeasureSpec.getSize(widthMeasureSpec);
		}
		if (mViewHeight == LayoutParams.MATCH_PARENT) {
			mViewHeight = MeasureSpec.getSize(heightMeasureSpec);
		}
		float scaleX = (float) mViewWidth / mImageWidth;
		float scaleY = (float) mViewHeight / mImageHeight;
		mScaleFit = Math.min(Math.min(scaleX, scaleY), mScaleMax);
		mScale = Math.min(mScaleFit, 1);
		mScaleMin = mScale;

	}

	public void setScaleMax(float mScaleMax) {
		this.mScaleMax = mScaleMax;
	}

	public float getScaleMax() {
		return mScaleMax;
	}

	public void setScaleMin(float mScaleMin) {
		this.mScaleMin = mScaleMin;
	}

	public float getScaleMin() {
		return mScaleMin;
	}

	private class ScaleListener extends
			ScaleGestureDetector.SimpleOnScaleGestureListener {
		int origX;
		int origY;

		@Override
		public boolean onScaleBegin(ScaleGestureDetector detector) {
			mState = STATE_ZOOM;
			return true;
		}

		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			float mScaleFactor = (float) Math.min(
					Math.max(.95f, detector.getScaleFactor()), 1.05);
			float origScale = mScale;
			origX = (int) ((detector.getFocusX() - mLeft) / mScale);
			origY = (int) ((detector.getFocusY() - mTop) / mScale);
			mScale *= mScaleFactor;
			if (mScale > mScaleMax) {
				mScale = mScaleMax;
				mScaleFactor = mScaleMax / origScale;
			} else if (mScale < mScaleMin) {
				mScale = mScaleMin;
				mScaleFactor = mScaleMin / origScale;
			}

			mLeft = (int) (detector.getFocusX() - origX * mScale);
			mTop = (int) (detector.getFocusY() - origY * mScale);

			return true;
		}
	}

	private class DoubleTapListener extends
			GestureDetector.SimpleOnGestureListener {

		@Override
		public boolean onDoubleTap(MotionEvent e) {
			if (mImageWidth == mViewWidth && mImageHeight == mViewHeight) {
				// if image dimensions are equal view dimensions do
				// nothing
				return true;
			} else if (mImageWidth < mViewWidth && mImageHeight < mViewHeight) {
				if (mScale != 1) {
					toOriginalScale(e, false);
				} else {
					toScaleFit(e, true);
				}
			} else {
				if (mScale == mScaleFit) {
					toOriginalScale(e, true);
				} else {
					toScaleFit(e, false);
				}
			}
			return true;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			return true;
		}

	}

	/**
	 * Zoom to original scale following a double tap. The image will be
	 * translated in a way that the point where the double tap was made will be
	 * centered in the View.
	 * 
	 * @param e
	 *            MotionEvent of the double tap.
	 * @param zoomIn
	 *            True if we are zooming in. False if we are zooming out.
	 */
	private void toOriginalScale(MotionEvent e, final boolean zoomIn) {
		final int steps = 10; // number of steps in zooming process
		final float targetScale = 1f; // target scale
		int eX; // X coordinate inside scaled image
		int eY; // Y coordinate inside scaled image
		int origX; // X coordinate inside unscaled image
		int origY; // Y coordinate inside unscaled image

		// if tap is outside image bounds, move coordinates inside image
		if (e.getX() < mLeft) {
			eX = 0;
		} else if (e.getX() > mRight) {
			eX = mRight - mLeft;
		} else {
			eX = (int) e.getX() - mLeft;
		}
		if (e.getY() < mTop) {
			eY = 0;
		} else if (e.getY() > mBottom) {
			eY = mBottom - mTop;
		} else {
			eY = (int) e.getY() - mTop;
		}

		origX = (int) (eX / mScale);
		origY = (int) (eY / mScale);

		// value of mLeft at the end of translation
		final int targetLeft = mViewWidth / 2 - origX;
		// value of mTop at the end of translation
		final int targetTop = mViewHeight / 2 - origY;
		// delta X: step to move in X axis
		final int dx = (targetLeft - mLeft) / steps;
		// delta Y: step to move in Y axis
		final int dy = (targetTop - mTop) / steps;
		// delta S: increment scale by delta S each step
		final float ds = (targetScale - mScale) / steps;
		post(new Runnable() {

			@Override
			public void run() {
				mLeft += dx;
				mTop += dy;
				if (zoomIn) {
					mScale = Math.min((mScale + ds), targetScale);

				} else {
					mScale = Math.max((mScale + ds), targetScale);
				}
				if (mScale == targetScale) {
					mLeft = targetLeft;
					mTop = targetTop;
				}
				invalidate();
				if (zoomIn) {
					if (mScale < targetScale) {
						post(this);
					}
				} else {
					if (mScale > targetScale) {
						post(this);
					}
				}

			}
		});
	}

	/**
	 * Zoom to {@link #mScaleFit} following a double tap.
	 * 
	 * @param e
	 *            MotionEvent of the double tap.
	 * @param zoomIn
	 *            True if we are zooming in. False if we are zooming out.
	 */
	private void toScaleFit(final MotionEvent e, final boolean zoomIn) {
		final int steps = 10; // number of steps in zooming process
		int eX; // X coordinate inside scaled image
		int eY; // Y coordinate inside scaled image
		// delta S: increment scale by delta S each step
		final float ds = (mScaleFit - mScale) / steps;

		// if tap is outside image bounds, move coordinates inside image
		if (e.getX() < mLeft) {
			eX = 0;
		} else if (e.getX() > mRight) {
			eX = mRight - mLeft;
		} else {
			eX = (int) e.getX() - mLeft;
		}
		if (e.getY() < mTop) {
			eY = 0;
		} else if (e.getY() > mBottom) {
			eY = mBottom - mTop;
		} else {
			eY = (int) e.getY() - mTop;
		}

		// X coordinate inside unscaled image
		final int origX = (int) (eX / mScale);
		// Y coordinate inside unscaled image
		final int origY = (int) (eY / mScale);
		post(new Runnable() {

			@Override
			public void run() {
				if (zoomIn) {
					mScale = Math.min((mScale + ds), mScaleFit);
				} else {
					mScale = Math.max((mScale + ds), mScaleFit);
				}
				mLeft = (int) (e.getX() - origX * mScale);
				mTop = (int) (e.getY() - origY * mScale);

				invalidate();
				if (zoomIn) {
					if (mScale < mScaleFit) {
						post(this);
					}
				} else {
					if (mScale > mScaleFit) {
						post(this);
					}
				}

			}
		});
	}

}
