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

import java.io.FileNotFoundException;
import java.io.InputStream;

import android.app.ActionBar.LayoutParams;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * ZoomImageView is an Android� custom View that supports pinch/double-tap
 * zooming and panning gestures.
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

	/** Context. */
	private Context mContext;

	/** Resource Id of the image to be displayed. */
	private int mResource = 0;

	/** Uri of the image to be displayed. */
	private Uri mUri;

	/** InputStream used to open the image. */
	private InputStream mInputImage;

	/** Drawable object used to display the image. */
	private Drawable mDrawable = null;

	/** Height of drawable. */
	private int mDrawableHeight;

	/** Width of drawable. */
	private int mDrawableWidth;

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

	/** Gesture detector for detecting simple gestures (e.g. double-tap, fling). */
	private GestureDetector mSimpleDetector;

	private ColorFilter mColorFilter;
	private int mAlpha = 255;
	private int mViewAlphaScale = 256;
	private boolean mColorMod = false;

	public ZoomImageView(Context context) {
		super(context);
		mContext = context;
	}

	public ZoomImageView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ZoomImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mContext = context;
		TypedArray a = getContext().obtainStyledAttributes(attrs,
				R.styleable.ZoomImageView);
		mResource = a.getResourceId(R.styleable.ZoomImageView_android_src, 0);
		int tint = a.getInt(R.styleable.ZoomImageView_android_tint, 0);
		if (tint != 0) {
			setColorFilter(tint);
		}

		int alpha = a.getInt(R.styleable.ZoomImageView_android_drawableAlpha,
				255);
		if (alpha != 255) {
			setAlpha(alpha);
		}
		setDrawablefromResource();
		a.recycle();
	}

	private void setDrawablefromResource() {
		if (mResource != 0) {
			mInputImage = getResources().openRawResource(mResource);
			updateDrawable(Drawable.createFromStream(mInputImage, "zoom_image"));
			invalidate();
		}
	}

	private void setDrawablefromUri() {
		/*
		 * TODO Find a way to load large images without getting an
		 * OutOfMemoryError when setting from Uri
		 */
		if (mUri != null) {
			try {
				mInputImage = mContext.getContentResolver().openInputStream(
						mUri);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				return;
			}
			Drawable d = Drawable.createFromStream(mInputImage, "zoom_image");

			updateDrawable(d);
			invalidate();
		}
	}

	private void updateDrawable(Drawable d) {
		if (mDrawable != null) {
			mDrawable.setCallback(null);
			unscheduleDrawable(mDrawable);
		}
		mDrawable = d;
		if (d != null) {
			d.setCallback(this);
			if (d.isStateful()) {
				d.setState(getDrawableState());
			}
			mDrawableWidth = d.getIntrinsicWidth();
			mDrawableHeight = d.getIntrinsicHeight();
			applyColorMod();
			requestLayout();
		} else {
			mDrawableWidth = mDrawableHeight = -1;
		}
		invalidate();
	}

	/**
	 * Some initializations.
	 * 
	 * @param context
	 */
	private void init() {
		if (mDrawable == null) {
			invalidate();
			return;
		}
		float scaleX = (float) mViewWidth / mDrawableWidth;
		float scaleY = (float) mViewHeight / mDrawableHeight;
		mScaleFit = Math.min(Math.min(scaleX, scaleY), mScaleMax);
		mScale = Math.min(mScaleFit, 1);
		mScaleMin = mScale;

		mScaleDetector = new ScaleGestureDetector(mContext, new ScaleListener());
		mSimpleDetector = new GestureDetector(mContext,
				new SimpleGestureListener());
		setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				mSimpleDetector.onTouchEvent(event);
				mScaleDetector.onTouchEvent(event);
				PointF pointCurr = new PointF(event.getX(), event.getY());

				switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					mPointLast.set(event.getX(), event.getY());
					mPointStart.set(mPointLast);
					mState = STATE_DRAG;
					break;

				case MotionEvent.ACTION_MOVE:
					if (mDrawableWidth < mViewWidth
							&& mDrawableHeight < mViewHeight) {
						if (mDrawableWidth * mScale > mViewWidth
								|| mDrawableHeight * mScale > mViewHeight) {
							getParent()
									.requestDisallowInterceptTouchEvent(true);
						}
					} else {
						if (mScale != mScaleFit) {
							getParent()
									.requestDisallowInterceptTouchEvent(true);
						}
					}
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
		if (mDrawable == null) {
			return;
		}

		if (mDrawableWidth == 0 || mDrawableHeight == 0) {
			return; // nothing to draw (empty bounds)
		}

		mRight = (int) (mScale * mDrawableWidth + mLeft);
		mBottom = (int) (mScale * mDrawableHeight + mTop);

		if (mDrawableWidth * mScale <= mViewWidth) {
			mLeft = (int) ((mViewWidth - mDrawableWidth * mScale) / 2);
		} else if (mLeft > 0) {
			mLeft = 0;
		} else if (mRight < mViewWidth) {
			mLeft += mViewWidth - mRight;
		}

		if (mDrawableHeight * mScale <= mViewHeight) {
			mTop = (int) ((mViewHeight - mDrawableHeight * mScale) / 2);
		} else if (mTop > 0) {
			mTop = 0;
		} else if (mBottom < mViewHeight) {
			mTop += mViewHeight - mBottom;
		}

		mRight = (int) (mScale * mDrawableWidth + mLeft);
		mBottom = (int) (mScale * mDrawableHeight + mTop);

		mDrawable.setBounds(mLeft, mTop, mRight, mBottom);
		mDrawable.draw(canvas);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		mViewWidth = getLayoutParams().width;
		mViewHeight = getLayoutParams().height;
		if (mViewWidth == LayoutParams.MATCH_PARENT
				|| mViewWidth == LayoutParams.WRAP_CONTENT) {
			mViewWidth = MeasureSpec.getSize(widthMeasureSpec);
		}
		if (mViewHeight == LayoutParams.MATCH_PARENT
				|| mViewHeight == LayoutParams.WRAP_CONTENT) {
			mViewHeight = MeasureSpec.getSize(heightMeasureSpec);
		}
		init();
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
					Math.max(.95f, detector.getScaleFactor()), 1.5);
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

	private class SimpleGestureListener extends
			GestureDetector.SimpleOnGestureListener {

		@Override
		public boolean onDoubleTap(MotionEvent e) {
			if (mDrawableWidth == mViewWidth && mDrawableHeight == mViewHeight) {
				// if image dimensions are equal to view dimensions do
				// nothing
				return true;
			} else if (mDrawableWidth < mViewWidth
					&& mDrawableHeight < mViewHeight) {
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
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			// Get velocity values
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

	public void setImageResource(int resId) {
		this.mResource = resId;
		setDrawablefromResource();
	}

	public void setImageUri(Uri uri) {
		if (mResource != 0
				|| (mUri != uri && (uri == null || mUri == null || !uri
						.equals(mUri)))) {
			updateDrawable(null);
			mResource = 0;
			this.mUri = uri;
			setDrawablefromUri();
		}
	}

	/**
	 * Sets a drawable as the content of this ImageView.
	 * 
	 * @param drawable
	 *            The drawable to set
	 */
	public void setImageDrawable(Drawable drawable) {
		if (mDrawable != drawable) {
			mResource = 0;
			mUri = null;
			updateDrawable(drawable);
		}
	}

	/**
	 * Set a tinting option for the image.
	 * 
	 * @param color
	 *            Color tint to apply.
	 * @param mode
	 *            How to apply the color. The standard mode is
	 *            {@link PorterDuff.Mode#SRC_ATOP}
	 */
	public final void setColorFilter(int color, PorterDuff.Mode mode) {
		setColorFilter(new PorterDuffColorFilter(color, mode));
	}

	/**
	 * Set a tinting option for the image. Assumes
	 * {@link PorterDuff.Mode#SRC_ATOP} blending mode.
	 * 
	 * @param color
	 *            Color tint to apply.
	 */
	public final void setColorFilter(int color) {
		setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
	}

	public final void clearColorFilter() {
		setColorFilter(null);
	}

	/**
	 * Apply an arbitrary colorfilter to the image.
	 * 
	 * @param cf
	 *            the colorfilter to apply (may be null)
	 */
	public void setColorFilter(ColorFilter cf) {
		if (mColorFilter != cf) {
			mColorFilter = cf;
			mColorMod = true;
			applyColorMod();
			invalidate();
		}
	}

	public void setAlpha(int alpha) {
		alpha &= 0xFF; // keep it legal
		if (mAlpha != alpha) {
			mAlpha = alpha;
			mColorMod = true;
			applyColorMod();
			invalidate();
		}
	}

	private void applyColorMod() {
		// Only mutate and apply when modifications have occurred. This should
		// not reset the mColorMod flag, since these filters need to be
		// re-applied if the Drawable is changed.
		if (mDrawable != null && mColorMod) {
			mDrawable = mDrawable.mutate();
			mDrawable.setColorFilter(mColorFilter);
			mDrawable.setAlpha(mAlpha * mViewAlphaScale >> 8);
		}
	}

}
