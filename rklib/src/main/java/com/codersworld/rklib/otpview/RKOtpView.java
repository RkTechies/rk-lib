package com.codersworld.rklib.otpview;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.InputFilter;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.MovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;
import com.codersworld.rklib.R;


public class RKOtpView extends AppCompatEditText {

  private static final boolean IS_DEBUG = false;
  private static final int BLINK_TIME = 500;
  private static final int DEFAULT_COUNT = 4;
  private static final InputFilter[] NO_FILTERS = new InputFilter[0];
  private static final int[] SELECTED_STATE = new int[] {
      android.R.attr.state_selected
  };
  private static final int[] FILLED_STATE = new int[] {
      R.attr.OtpState_filled
  };
  private static final int VIEW_TYPE_RECTANGLE = 0;
  private static final int VIEW_TYPE_LINE = 1;
  private static final int VIEW_TYPE_NONE = 2;
  private int viewType;
  private int otpViewItemCount;
  private int otpViewItemWidth;
  private int otpViewItemHeight;
  private int otpViewItemRadius;
  private int otpViewItemSpacing;
  private final Paint paint;
  private final TextPaint animatorTextPaint = new TextPaint();
  private ColorStateList lineColor;
  private int cursorLineColor = Color.BLACK;
  private int lineWidth;
  private final Rect textRect = new Rect();
  private final RectF itemBorderRect = new RectF();
  private final RectF itemLineRect = new RectF();
  private final Path path = new Path();
  private final PointF itemCenterPoint = new PointF();
  private ValueAnimator defaultAddAnimator;
  private boolean isAnimationEnable = false;
  private Blink blink;
  private boolean isCursorVisible;
  private boolean drawCursor;
  private float cursorHeight;
  private int cursorWidth;
  private int cursorColor;
  private int itemBackgroundResource;
  private Drawable itemBackground;
  private boolean hideLineWhenFilled;
  private boolean rtlTextDirection;
  private String maskingChar;
  private boolean isAllCaps = false;
  private OnOtpCompletionListener onOtpCompletionListener;

  public RKOtpView(Context context) {
    this(context, null);
  }

  public RKOtpView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.rkOtpViewStyle);
  }

  public RKOtpView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    final Resources res = getResources();
    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setStyle(Paint.Style.STROKE);
    animatorTextPaint.set(getPaint());
    final Resources.Theme theme = context.getTheme();
    TypedArray typedArray =
        theme.obtainStyledAttributes(attrs, R.styleable.RkOtpView, defStyleAttr, 0);
    viewType = typedArray.getInt(R.styleable.RkOtpView_OtpViewType, VIEW_TYPE_NONE);
    otpViewItemCount = typedArray.getInt(R.styleable.RkOtpView_OtpItemCount, DEFAULT_COUNT);
    otpViewItemHeight = (int) typedArray.getDimension(R.styleable.RkOtpView_OtpItemHeight,
        res.getDimensionPixelSize(R.dimen.otp_view_item_size));
    otpViewItemWidth = (int) typedArray.getDimension(R.styleable.RkOtpView_OtpItemWidth,
        res.getDimensionPixelSize(R.dimen.otp_view_item_size));
    otpViewItemSpacing = typedArray.getDimensionPixelSize(R.styleable.RkOtpView_OtpItemSpacing,
        res.getDimensionPixelSize(R.dimen.otp_view_item_spacing));
    otpViewItemRadius = (int) typedArray.getDimension(R.styleable.RkOtpView_OtpItemRadius, 0);
    lineWidth = (int) typedArray.getDimension(R.styleable.RkOtpView_OtpLineWidth,
        res.getDimensionPixelSize(R.dimen.otp_view_item_line_width));
    lineColor = typedArray.getColorStateList(R.styleable.RkOtpView_OtpLineColor);
    isCursorVisible = typedArray.getBoolean(R.styleable.RkOtpView_android_cursorVisible, true);
    cursorColor = typedArray.getColor(R.styleable.RkOtpView_OtpCursorColor, getCurrentTextColor());
    cursorWidth = typedArray.getDimensionPixelSize(R.styleable.RkOtpView_OtpCursorWidth,
        res.getDimensionPixelSize(R.dimen.otp_view_cursor_width));
    itemBackground = typedArray.getDrawable(R.styleable.RkOtpView_android_itemBackground);
    hideLineWhenFilled = typedArray.getBoolean(R.styleable.RkOtpView_OtpHideLineWhenFilled, false);
    rtlTextDirection = typedArray.getBoolean(R.styleable.RkOtpView_OtpRtlTextDirection, false);
    maskingChar = typedArray.getString(R.styleable.RkOtpView_OtpMaskingChar);
    isAllCaps = typedArray.getBoolean(R.styleable.RkOtpView_android_textAllCaps,false);
    typedArray.recycle();
    if (lineColor != null) {
      cursorLineColor = lineColor.getDefaultColor();
    }
    updateCursorHeight();
    checkItemRadius();
    setMaxLength(otpViewItemCount);
    paint.setStrokeWidth(lineWidth);
    setupAnimator();
    setTextIsSelectable(false);
  }

  @Override
  public void setTypeface(Typeface tf, int style) {
    super.setTypeface(tf, style);
  }

  @Override
  public void setTypeface(Typeface tf) {
    super.setTypeface(tf);
    if (animatorTextPaint != null) {
      animatorTextPaint.set(getPaint());
    }
  }

  private void setMaxLength(int maxLength) {
    setFilters(
        maxLength >= 0 ? new InputFilter[] { new InputFilter.LengthFilter(maxLength) }
            : NO_FILTERS);
  }

  private void setupAnimator() {
    defaultAddAnimator = ValueAnimator.ofFloat(0.5f, 1f);
    defaultAddAnimator.setDuration(150);
    defaultAddAnimator.setInterpolator(new DecelerateInterpolator());
    defaultAddAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        float scale = (float) animation.getAnimatedValue();
        int alpha = (int) (255 * scale);
        animatorTextPaint.setTextSize(getTextSize() * scale);
        animatorTextPaint.setAlpha(alpha);
        postInvalidate();
      }
    });
  }

  private void checkItemRadius() {
    if (viewType == VIEW_TYPE_LINE) {
      float halfOfLineWidth = ((float) lineWidth) / 2;
      if (otpViewItemRadius > halfOfLineWidth) {
        throw new IllegalArgumentException(
            "The itemRadius can not be greater than lineWidth when viewType is line");
      }
    } else if (viewType == VIEW_TYPE_RECTANGLE) {
      float halfOfItemWidth = ((float) otpViewItemWidth) / 2;
      if (otpViewItemRadius > halfOfItemWidth) {
        throw new IllegalArgumentException("The itemRadius can not be greater than itemWidth");
      }
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    int heightSize = MeasureSpec.getSize(heightMeasureSpec);
    int width;
    int height;
    int boxHeight = otpViewItemHeight;
    if (widthMode == MeasureSpec.EXACTLY) {
      width = widthSize;
    } else {
      int boxesWidth =
          (otpViewItemCount - 1) * otpViewItemSpacing + otpViewItemCount * otpViewItemWidth;
      width = boxesWidth + ViewCompat.getPaddingEnd(this) + ViewCompat.getPaddingStart(this);
      if (otpViewItemSpacing == 0) {
        width -= (otpViewItemCount - 1) * lineWidth;
      }
    }
    height = heightMode == MeasureSpec.EXACTLY ? heightSize
        : boxHeight + getPaddingTop() + getPaddingBottom();
    setMeasuredDimension(width, height);
  }

  @Override
  protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
    if (start != text.length()) {
      moveSelectionToEnd();
    }
    if (text.length() == otpViewItemCount && onOtpCompletionListener != null) {
      onOtpCompletionListener.onOtpCompleted(text.toString());
    }
    makeBlink();
    if (isAnimationEnable) {
      final boolean isAdd = lengthAfter - lengthBefore > 0;
      if (isAdd && defaultAddAnimator != null) {
        defaultAddAnimator.end();
        defaultAddAnimator.start();
      }
    }
  }

  @Override
  protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
    super.onFocusChanged(focused, direction, previouslyFocusedRect);
    if (focused) {
      moveSelectionToEnd();
      makeBlink();
    }
  }

  @Override
  protected void onSelectionChanged(int selStart, int selEnd) {
    super.onSelectionChanged(selStart, selEnd);
    if (getText() != null && selEnd != getText().length()) {
      moveSelectionToEnd();
    }
  }

  private void moveSelectionToEnd() {
    if (getText() != null) {
      setSelection(getText().length());
    }
  }

  @Override
  protected void drawableStateChanged() {
    super.drawableStateChanged();
    if (lineColor == null || lineColor.isStateful()) {
      updateColors();
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.save();
    updatePaints();
    drawOtpView(canvas);
    canvas.restore();
  }

  private void updatePaints() {
    paint.setColor(cursorLineColor);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(lineWidth);
    getPaint().setColor(getCurrentTextColor());
  }

  private void drawOtpView(Canvas canvas) {
    int nextItemToFill;
    if (rtlTextDirection) {
      nextItemToFill = otpViewItemCount - 1;
    } else {
      if (getText() != null) {
        nextItemToFill = getText().length();
      } else {
        nextItemToFill = 0;
      }
    }
    for (int i = 0; i < otpViewItemCount; i++) {
      boolean itemSelected = isFocused() && nextItemToFill == i;
      boolean itemFilled = i < nextItemToFill;
      int[] itemState = null;
      if (itemFilled) {
        itemState = FILLED_STATE;
      } else if (itemSelected) {
        itemState = SELECTED_STATE;
      }
      paint.setColor(itemState != null ? getLineColorForState(itemState) : cursorLineColor);
      updateItemRectF(i);
      updateCenterPoint();
      canvas.save();
      if (viewType == VIEW_TYPE_RECTANGLE) {
        updateOtpViewBoxPath(i);
        canvas.clipPath(path);
      }
      drawItemBackground(canvas, itemState);
      canvas.restore();
      if (itemSelected) {
        drawCursor(canvas);
      }
      if (viewType == VIEW_TYPE_RECTANGLE) {
        drawOtpBox(canvas, i);
      } else if (viewType == VIEW_TYPE_LINE) {
        drawOtpLine(canvas, i);
      }
      if (IS_DEBUG) {
        drawAnchorLine(canvas);
      }
      if (rtlTextDirection) {
        int reversedPosition = otpViewItemCount - i;
        if (getText().length() >= reversedPosition) {
          drawInput(canvas, i);
        } else if (!TextUtils.isEmpty(getHint()) && getHint().length() == otpViewItemCount) {
          drawHint(canvas, i);
        }
      } else {
        if (getText().length() > i) {
          drawInput(canvas, i);
        } else if (!TextUtils.isEmpty(getHint()) && getHint().length() == otpViewItemCount) {
          drawHint(canvas, i);
        }
      }
    }
    if (isFocused()
        && getText() != null
        && getText().length() != otpViewItemCount
        && viewType == VIEW_TYPE_RECTANGLE) {
      int index = getText().length();
      updateItemRectF(index);
      updateCenterPoint();
      updateOtpViewBoxPath(index);
      paint.setColor(getLineColorForState(SELECTED_STATE));
      drawOtpBox(canvas, index);
    }
  }

  private void drawInput(Canvas canvas, int i) {
    //allows masking for all number keyboard
    if (maskingChar != null &&
        (isNumberInputType(getInputType()) ||
            isPasswordInputType(getInputType()))) {
      drawMaskingText(canvas, i, Character.toString(maskingChar.charAt(0)));
    } else if (isPasswordInputType(getInputType())) {
      drawCircle(canvas, i);
    } else {
      drawText(canvas, i);
    }
  }

  private int getLineColorForState(int... states) {
    return lineColor != null ? lineColor.getColorForState(states,
        cursorLineColor) : cursorLineColor;
  }

  private void drawItemBackground(Canvas canvas, int[] backgroundState) {
    if (itemBackground == null) {
      return;
    }
    float delta = (float) lineWidth / 2;
    int left = Math.round(itemBorderRect.left - delta);
    int top = Math.round(itemBorderRect.top - delta);
    int right = Math.round(itemBorderRect.right + delta);
    int bottom = Math.round(itemBorderRect.bottom + delta);
    itemBackground.setBounds(left, top, right, bottom);
    if(viewType != VIEW_TYPE_NONE) {
      itemBackground.setState(backgroundState != null ? backgroundState : getDrawableState());
    }
    itemBackground.draw(canvas);
  }

  private void updateOtpViewBoxPath(int i) {
    boolean drawRightCorner = false;
    boolean drawLeftCorner = false;
    if (otpViewItemSpacing != 0) {
      drawLeftCorner = drawRightCorner = true;
    } else {
      if (i == 0 && i != otpViewItemCount - 1) {
        drawLeftCorner = true;
      }
      if (i == otpViewItemCount - 1 && i != 0) {
        drawRightCorner = true;
      }
    }
    updateRoundRectPath(itemBorderRect, otpViewItemRadius, otpViewItemRadius, drawLeftCorner,
        drawRightCorner);
  }

  private void drawOtpBox(Canvas canvas, int i) {
    if (getText() != null && hideLineWhenFilled && i < getText().length()) {
      return;
    }
    canvas.drawPath(path, paint);
  }

  private void drawOtpLine(Canvas canvas, int i) {
    if (getText() != null && hideLineWhenFilled && i < getText().length()) {
      return;
    }
    boolean drawLeft;
    boolean drawRight;
    drawLeft = drawRight = true;
    if (otpViewItemSpacing == 0 && otpViewItemCount > 1) {
      if (i == 0) {
        drawRight = false;
      } else if (i == otpViewItemCount - 1) {
        drawLeft = false;
      } else {
        drawLeft = drawRight = false;
      }
    }
    paint.setStyle(Paint.Style.FILL);
    paint.setStrokeWidth(((float) lineWidth) / 10);
    float halfLineWidth = ((float) lineWidth) / 2;
    itemLineRect.set(
        itemBorderRect.left - halfLineWidth,
        itemBorderRect.bottom - halfLineWidth,
        itemBorderRect.right + halfLineWidth,
        itemBorderRect.bottom + halfLineWidth);

    updateRoundRectPath(itemLineRect, otpViewItemRadius, otpViewItemRadius, drawLeft, drawRight);
    canvas.drawPath(path, paint);
  }

  private void drawCursor(Canvas canvas) {
    if (drawCursor) {
      float cx = itemCenterPoint.x;
      float cy = itemCenterPoint.y;
      float y = cy - cursorHeight / 2;
      int color = paint.getColor();
      float width = paint.getStrokeWidth();
      paint.setColor(cursorColor);
      paint.setStrokeWidth(cursorWidth);
      canvas.drawLine(cx, y, cx, y + cursorHeight, paint);
      paint.setColor(color);
      paint.setStrokeWidth(width);
    }
  }

  private void updateRoundRectPath(RectF rectF, float rx, float ry, boolean l, boolean r) {
    updateRoundRectPath(rectF, rx, ry, l, r, r, l);
  }

  private void updateRoundRectPath(RectF rectF, float rx, float ry,
      boolean tl, boolean tr, boolean br, boolean bl) {
    path.reset();
    float l = rectF.left;
    float t = rectF.top;
    float r = rectF.right;
    float b = rectF.bottom;
    float w = r - l;
    float h = b - t;
    float lw = w - 2 * rx;
    float lh = h - 2 * ry;
    path.moveTo(l, t + ry);
    if (tl) {
      path.rQuadTo(0, -ry, rx, -ry);
    } else {
      path.rLineTo(0, -ry);
      path.rLineTo(rx, 0);
    }
    path.rLineTo(lw, 0);
    if (tr) {
      path.rQuadTo(rx, 0, rx, ry);
    } else {
      path.rLineTo(rx, 0);
      path.rLineTo(0, ry);
    }
    path.rLineTo(0, lh);
    if (br) {
      path.rQuadTo(0, ry, -rx, ry);
    } else {
      path.rLineTo(0, ry);
      path.rLineTo(-rx, 0);
    }
    path.rLineTo(-lw, 0);
    if (bl) {
      path.rQuadTo(-rx, 0, -rx, -ry);
    } else {
      path.rLineTo(-rx, 0);
      path.rLineTo(0, -ry);
    }
    path.rLineTo(0, -lh);
    path.close();
  }

  private void updateItemRectF(int i) {
    float halfLineWidth = ((float) lineWidth) / 2;
    float left = getScrollX()
        + ViewCompat.getPaddingStart(this)
        + i * (otpViewItemSpacing + otpViewItemWidth)
        + halfLineWidth;
    if (otpViewItemSpacing == 0 && i > 0) {
      left = left - (lineWidth) * i;
    }
    float right = left + otpViewItemWidth - lineWidth;
    float top = getScrollY() + getPaddingTop() + halfLineWidth;
    float bottom = top + otpViewItemHeight - lineWidth;
    itemBorderRect.set(left, top, right, bottom);
  }

  private void drawText(Canvas canvas, int i) {
    Paint paint = getPaintByIndex(i);
    paint.setColor(getCurrentTextColor());
    if (rtlTextDirection) {
      int reversedPosition = otpViewItemCount - i;
      int reversedCharPosition;
      if (getText() == null) {
        reversedCharPosition = reversedPosition;
      } else {
        reversedCharPosition = reversedPosition - getText().length();
      }
      if (reversedCharPosition <= 0 && getText() != null) {
        drawTextAtBox(canvas, paint, getText(), Math.abs(reversedCharPosition));
      }
    } else if (getText() != null) {
      drawTextAtBox(canvas, paint, getText(), i);
    }
  }

  private void drawMaskingText(Canvas canvas, int i, String maskingChar) {
    Paint paint = getPaintByIndex(i);
    paint.setColor(getCurrentTextColor());
    if (rtlTextDirection) {
      int reversedPosition = otpViewItemCount - i;
      int reversedCharPosition;
      if (getText() == null) {
        reversedCharPosition = reversedPosition;
      } else {
        reversedCharPosition = reversedPosition - getText().length();
      }
      if (reversedCharPosition <= 0 && getText() != null) {
        drawTextAtBox(canvas, paint, getText().toString().replaceAll(".", maskingChar),
            Math.abs(reversedCharPosition));
      }
    } else if (getText() != null) {
      drawTextAtBox(canvas, paint, getText().toString().replaceAll(".", maskingChar), i);
    }
  }

  private void drawHint(Canvas canvas, int i) {
    Paint paint = getPaintByIndex(i);
    paint.setColor(getCurrentHintTextColor());
    if (rtlTextDirection) {
      int reversedPosition = otpViewItemCount - i;
      int reversedCharPosition = reversedPosition - getHint().length();
      if (reversedCharPosition <= 0) {
        drawTextAtBox(canvas, paint, getHint(), Math.abs(reversedCharPosition));
      }
    } else {
      drawTextAtBox(canvas, paint, getHint(), i);
    }
  }

  private void drawTextAtBox(Canvas canvas, Paint paint, CharSequence text, int charAt) {
    paint.getTextBounds(text.toString(), charAt, charAt + 1, textRect);
    float cx = itemCenterPoint.x;
    float cy = itemCenterPoint.y;
    float x = cx - Math.abs((float) textRect.width()) / 2 - textRect.left;
    float y = cy + Math.abs((float) textRect.height()) / 2 - textRect.bottom;
    if(isAllCaps){
      canvas.drawText(text.toString().toUpperCase(), charAt, charAt + 1, x, y, paint);
    }else{
      canvas.drawText(text, charAt, charAt + 1, x, y, paint);
    }
  }

  private void drawCircle(Canvas canvas, int i) {
    Paint paint = getPaintByIndex(i);
    float cx = itemCenterPoint.x;
    float cy = itemCenterPoint.y;
    if (rtlTextDirection) {
      int reversedItemPosition = otpViewItemCount - i;
      int reversedCharPosition = reversedItemPosition - getHint().length();
      if (reversedCharPosition <= 0) {
        canvas.drawCircle(cx, cy, paint.getTextSize() / 2, paint);
      }
    } else {
      canvas.drawCircle(cx, cy, paint.getTextSize() / 2, paint);
    }
  }

  private Paint getPaintByIndex(int i) {
    if (getText() != null && isAnimationEnable && i == getText().length() - 1) {
      animatorTextPaint.setColor(getPaint().getColor());
      return animatorTextPaint;
    } else {
      return getPaint();
    }
  }

  private void drawAnchorLine(Canvas canvas) {
    float cx = itemCenterPoint.x;
    float cy = itemCenterPoint.y;
    paint.setStrokeWidth(1);
    cx -= paint.getStrokeWidth() / 2;
    cy -= paint.getStrokeWidth() / 2;
    path.reset();
    path.moveTo(cx, itemBorderRect.top);
    path.lineTo(cx, itemBorderRect.top + Math.abs(itemBorderRect.height()));
    canvas.drawPath(path, paint);
    path.reset();
    path.moveTo(itemBorderRect.left, cy);
    path.lineTo(itemBorderRect.left + Math.abs(itemBorderRect.width()), cy);
    canvas.drawPath(path, paint);
    path.reset();
    paint.setStrokeWidth(lineWidth);
  }

  private void updateColors() {
    boolean shouldInvalidate = false;
    int color = lineColor != null ? lineColor.getColorForState(getDrawableState(), 0)
        : getCurrentTextColor();
    if (color != cursorLineColor) {
      cursorLineColor = color;
      shouldInvalidate = true;
    }
    if (shouldInvalidate) {
      invalidate();
    }
  }

  private void updateCenterPoint() {
    float cx = itemBorderRect.left + Math.abs(itemBorderRect.width()) / 2;
    float cy = itemBorderRect.top + Math.abs(itemBorderRect.height()) / 2;
    itemCenterPoint.set(cx, cy);
  }

  private static boolean isPasswordInputType(int inputType) {
    final int variation =
        inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
    return variation
        == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD)
        || variation
        == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        || variation
        == (EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD);
  }

  private static boolean isNumberInputType(int inputType) {
    return inputType == EditorInfo.TYPE_CLASS_NUMBER;
  }

  @Override
  protected MovementMethod getDefaultMovementMethod() {
    return DefaultMovementMethod.getInstance();
  }

  public void setLineColor(@ColorInt int color) {
    lineColor = ColorStateList.valueOf(color);
    updateColors();
  }


  public void setLineColor(ColorStateList colors) {
    if (colors == null) {
      throw new IllegalArgumentException("Color cannot be null");
    }

    lineColor = colors;
    updateColors();
  }

  public ColorStateList getLineColors() {
    return lineColor;
  }

  @ColorInt
  public int getCurrentLineColor() {
    return cursorLineColor;
  }


  public void setLineWidth(@Px int borderWidth) {
    lineWidth = borderWidth;
    checkItemRadius();
    requestLayout();
  }


  public int getLineWidth() {
    return lineWidth;
  }


  public void setItemCount(int count) {
    otpViewItemCount = count;
    setMaxLength(count);
    requestLayout();
  }


  public int getItemCount() {
    return otpViewItemCount;
  }

  public void setItemRadius(@Px int itemRadius) {
    otpViewItemRadius = itemRadius;
    checkItemRadius();
    requestLayout();
  }


  public int getItemRadius() {
    return otpViewItemRadius;
  }


  public void setItemSpacing(@Px int itemSpacing) {
    otpViewItemSpacing = itemSpacing;
    requestLayout();
  }

  /**
   * @return Returns the spacing between two items.
   * @see #setItemSpacing(int)
   */
  @Px
  public int getItemSpacing() {
    return otpViewItemSpacing;
  }


  public void setItemHeight(@Px int itemHeight) {
    otpViewItemHeight = itemHeight;
    updateCursorHeight();
    requestLayout();
  }

  public int getItemHeight() {
    return otpViewItemHeight;
  }


  public void setItemWidth(@Px int itemWidth) {
    otpViewItemWidth = itemWidth;
    checkItemRadius();
    requestLayout();
  }

  public int getItemWidth() {
    return otpViewItemWidth;
  }

  public void setAnimationEnable(boolean enable) {
    isAnimationEnable = enable;
  }

  public void setHideLineWhenFilled(boolean hideLineWhenFilled) {
    this.hideLineWhenFilled = hideLineWhenFilled;
  }

  @Override
  public void setTextSize(float size) {
    super.setTextSize(size);
    updateCursorHeight();
  }

  @Override
  public void setTextSize(int unit, float size) {
    super.setTextSize(unit, size);
    updateCursorHeight();
  }

  public void setOtpCompletionListener(OnOtpCompletionListener otpCompletionListener) {
    this.onOtpCompletionListener = otpCompletionListener;
  }


  public void setItemBackgroundResources(@DrawableRes int resId) {
    if (resId != 0 && itemBackgroundResource != resId) {
      return;
    }
    itemBackground = ResourcesCompat.getDrawable(getResources(), resId, getContext().getTheme());
    setItemBackground(itemBackground);
    itemBackgroundResource = resId;
  }

  public void setItemBackgroundColor(@ColorInt int color) {
    if (itemBackground instanceof ColorDrawable) {
      ((ColorDrawable) itemBackground.mutate()).setColor(color);
      itemBackgroundResource = 0;
    } else {
      setItemBackground(new ColorDrawable(color));
    }
  }

  public void setItemBackground(Drawable background) {
    itemBackgroundResource = 0;
    itemBackground = background;
    invalidate();
  }

  public void setCursorWidth(@Px int width) {
    cursorWidth = width;
    if (isCursorVisible()) {
      invalidateCursor(true);
    }
  }

  public int getCursorWidth() {
    return cursorWidth;
  }


  public void setCursorColor(@ColorInt int color) {
    cursorColor = color;
    if (isCursorVisible()) {
      invalidateCursor(true);
    }
  }


  public int getCursorColor() {
    return cursorColor;
  }

  public void setMaskingChar(String maskingChar) {
    this.maskingChar = maskingChar;
    requestLayout();
  }

  public String getMaskingChar() {
    return maskingChar;
  }

  @Override
  public void setCursorVisible(boolean visible) {
    if (isCursorVisible != visible) {
      isCursorVisible = visible;
      invalidateCursor(isCursorVisible);
      makeBlink();
    }
  }

  @Override
  public boolean isCursorVisible() {
    return isCursorVisible;
  }

  @Override
  public void onScreenStateChanged(int screenState) {
    super.onScreenStateChanged(screenState);
    if (screenState == View.SCREEN_STATE_ON) {
      resumeBlink();
    } else if (screenState == View.SCREEN_STATE_OFF) {
      suspendBlink();
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    resumeBlink();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    suspendBlink();
  }

  private boolean shouldBlink() {
    return isCursorVisible() && isFocused();
  }

  private void makeBlink() {
    if (shouldBlink()) {
      if (blink == null) {
        blink = new Blink();
      }
      removeCallbacks(blink);
      drawCursor = false;
      postDelayed(blink, BLINK_TIME);
    } else {
      if (blink != null) {
        removeCallbacks(blink);
      }
    }
  }

  private void suspendBlink() {
    if (blink != null) {
      blink.cancel();
      invalidateCursor(false);
    }
  }

  private void resumeBlink() {
    if (blink != null) {
      blink.unCancel();
      makeBlink();
    }
  }

  private void invalidateCursor(boolean showCursor) {
    if (drawCursor != showCursor) {
      drawCursor = showCursor;
      invalidate();
    }
  }

  private void updateCursorHeight() {
    int delta = 2 * dpToPx();
    cursorHeight =
        otpViewItemHeight - getTextSize() > delta ? getTextSize() + delta : getTextSize();
  }

  private class Blink implements Runnable {
    private boolean cancelled;

    @Override
    public void run() {
      if (cancelled) {
        return;
      }

      removeCallbacks(this);

      if (shouldBlink()) {
        invalidateCursor(!drawCursor);
        postDelayed(this, BLINK_TIME);
      }
    }

    private void cancel() {
      if (!cancelled) {
        removeCallbacks(this);
        cancelled = true;
      }
    }

    private void unCancel() {
      cancelled = false;
    }
  }

  private int dpToPx() {
    return (int) ((float) 2 * getResources().getDisplayMetrics().density + 0.5f);
  }
}