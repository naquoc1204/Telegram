package org.telegram.ui;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.DrawingInBackgroundThreadDrawable;
import org.telegram.ui.Components.EmojiTabsStrip;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerCategoriesListView;
import java.util.ArrayList;

public class SelectAnimatedEmojiDialogTest1 extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public final static int TYPE_EMOJI_STATUS = 0;
    public final static int TYPE_REACTIONS = 1;
    public final static int TYPE_SET_DEFAULT_REACTION = 2;
    public static final int TYPE_TOPIC_ICON = 3;
    public static final int TYPE_AVATAR_CONSTRUCTOR = 4;
    public boolean forUser;
    public void setForUser(boolean forUser) {
        this.forUser = forUser;
    }

    @SuppressLint("SoonBlockedPrivateApi")
    public static class SelectAnimatedEmojiDialogWindow extends PopupWindow {
        private ViewTreeObserver.OnScrollChangedListener mSuperScrollListener;
        private ViewTreeObserver mViewTreeObserver;
        private static final ViewTreeObserver.OnScrollChangedListener NOP = () -> {
            /* do nothing */
        };

        public SelectAnimatedEmojiDialogWindow(View anchor) {
            super(anchor);
            init();
        }

        public SelectAnimatedEmojiDialogWindow(View anchor, int width, int height) {
            super(anchor, width, height);
            init();
        }

        private void init() {
            setFocusable(true);
            setAnimationStyle(0);
            setOutsideTouchable(true);
            setClippingEnabled(true);
            setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_FROM_FOCUSABLE);
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        private void unregisterListener() {
            if (mSuperScrollListener != null && mViewTreeObserver != null) {
                if (mViewTreeObserver.isAlive()) {
                    mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
                }
                mViewTreeObserver = null;
            }
        }

        private void registerListener(View anchor) {
            if (getContentView() instanceof SelectAnimatedEmojiDialogTest1) {
                ((SelectAnimatedEmojiDialogTest1) getContentView()).onShow(this::dismiss);
            }
            if (mSuperScrollListener != null) {
                ViewTreeObserver vto = (anchor.getWindowToken() != null) ? anchor.getViewTreeObserver() : null;
                if (vto != mViewTreeObserver) {
                    if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
                        mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
                    }
                    if ((mViewTreeObserver = vto) != null) {
                        vto.addOnScrollChangedListener(mSuperScrollListener);
                    }
                }
            }
        }

        public void dimBehind() {
            View container = getContentView().getRootView();
            Context context = getContentView().getContext();
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) container.getLayoutParams();
            p.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            p.dimAmount = 0.2f;
            wm.updateViewLayout(container, p);
        }

        private void dismissDim() {
            View container = getContentView().getRootView();
            Context context = getContentView().getContext();
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

            if (container.getLayoutParams() == null || !(container.getLayoutParams() instanceof WindowManager.LayoutParams)) {
                return;
            }
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) container.getLayoutParams();
            try {
                if ((p.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
                    p.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                    p.dimAmount = 0.0f;
                    wm.updateViewLayout(container, p);
                }
            } catch (Exception ignore) {
            }
        }

        @Override
        public void showAsDropDown(View anchor) {
            super.showAsDropDown(anchor);
            registerListener(anchor);
        }

        @Override
        public void showAsDropDown(View anchor, int xoff, int yoff) {
            super.showAsDropDown(anchor, xoff, yoff);
            registerListener(anchor);
        }

        @Override
        public void showAsDropDown(View anchor, int xoff, int yoff, int gravity) {
            super.showAsDropDown(anchor, xoff, yoff, gravity);
            registerListener(anchor);
        }

        @Override
        public void showAtLocation(View parent, int gravity, int x, int y) {
            super.showAtLocation(parent, gravity, x, y);
            unregisterListener();
        }

        @Override
        public void dismiss() {
            if (getContentView() instanceof SelectAnimatedEmojiDialogTest1) {
                ((SelectAnimatedEmojiDialogTest1) getContentView()).onDismiss(super::dismiss);
                dismissDim();
            } else {
                super.dismiss();
            }
        }
    }

    private final static int currentAccount = UserConfig.selectedAccount;
    private int type;

    public FrameLayout contentView;
    public FrameLayout gridViewContainer;
    public SelectAnimatedEmojiDialog.EmojiListView emojiSearchGridView;
    private View bubble1View;
    private View bubble2View;
    private SparseIntArray positionToExpand = new SparseIntArray();
    private ArrayList<EmojiView.EmojiPack> packs = new ArrayList<>();
    public boolean includeHint = false;
    private boolean drawBackground = true;
    public boolean cancelPressed;
    ImageReceiver bigReactionImageReceiver = new ImageReceiver();
    private View emojiTabsShadow;
    private float scaleX, scaleY;
    private int topMarginDp;
    private View animateExpandFromButton;

    public SelectAnimatedEmojiDialogTest1(BaseFragment baseFragment, Context context, Integer emojiX, int type, Theme.ResourcesProvider resourcesProvider) {
        this(baseFragment, context, emojiX, type, resourcesProvider, 16);
    }

    public SelectAnimatedEmojiDialogTest1(BaseFragment baseFragment, Context context, Integer emojiX, int type, Theme.ResourcesProvider resourcesProvider, int topPaddingDp) {
        super(context);
        this.type = type;
        this.includeHint = MessagesController.getGlobalMainSettings().getInt("emoji" + (type == TYPE_EMOJI_STATUS ? "status" : "reaction") + "usehint", 0) < 3;

        premiumStarColorFilter = new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider), PorterDuff.Mode.SRC_IN);

        final Integer bubbleX = emojiX == null ? null : MathUtils.clamp(emojiX, AndroidUtilities.dp(26), AndroidUtilities.dp(340 - 48));
        boolean bubbleRight = bubbleX != null && bubbleX > AndroidUtilities.dp(170);


        setFocusableInTouchMode(true);
        if (type == TYPE_EMOJI_STATUS || type == TYPE_SET_DEFAULT_REACTION) {
            topMarginDp = topPaddingDp;
            setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
            setOnTouchListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_DOWN && dismiss != null) {
                    dismiss.run();
                    return true;
                }
                return false;
            });
        }
        if (bubbleX != null) {
            bubble1View = new View(context);
            Drawable bubble1Drawable = getResources().getDrawable(R.drawable.shadowed_bubble1).mutate();
            bubble1Drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            bubble1View.setBackground(bubble1Drawable);
            addView(bubble1View, LayoutHelper.createFrame(10, 10, Gravity.TOP | Gravity.LEFT, bubbleX / AndroidUtilities.density + (bubbleRight ? -12 : 4), topMarginDp, 0, 0));
        }
        contentView = new FrameLayout(context) {
            private Path path = new Path();
            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (!drawBackground) {
                    super.dispatchDraw(canvas);
                    return;
                }
                canvas.save();
                Theme.applyDefaultShadow(paint);
                paint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
                paint.setAlpha((int) (255 * getAlpha()));
                float px = (bubbleX == null ? getWidth() / 2f : bubbleX) + AndroidUtilities.dp(20);
                float w = getWidth() - getPaddingLeft() - getPaddingRight();
                float h = getHeight() - getPaddingBottom() - getPaddingTop();
                AndroidUtilities.rectTmp.set(
                        getPaddingLeft() + (px - px * scaleX),
                        getPaddingTop(),
                        getPaddingLeft() + px + (w - px) * scaleX,
                        getPaddingTop() + h * scaleY
                );
                path.rewind();
                path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(12), AndroidUtilities.dp(12), Path.Direction.CW);
                canvas.drawPath(path, paint);
//                if (enterAnimationInProgress() {
                canvas.clipPath(path);
//                }
                super.dispatchDraw(canvas);
                canvas.restore();
            }
        };
        if (type == TYPE_EMOJI_STATUS || type == TYPE_SET_DEFAULT_REACTION) {
            contentView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        }
        addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, type == TYPE_EMOJI_STATUS || type == TYPE_SET_DEFAULT_REACTION ? 6 + topMarginDp : 0, 0, 0));

        if (bubbleX != null) {
            bubble2View = new View(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    setPivotX(getMeasuredWidth() / 2);
                    setPivotY(getMeasuredHeight());
                }
            };
            Drawable bubble2Drawable = getResources().getDrawable(R.drawable.shadowed_bubble2_half);
            bubble2Drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            bubble2View.setBackground(bubble2Drawable);
            addView(bubble2View, LayoutHelper.createFrame(17, 9, Gravity.TOP | Gravity.LEFT, bubbleX / AndroidUtilities.density + (bubbleRight ? -25 : 10), 6 + 8 - 9 + topMarginDp, 0, 0));
        }

        boolean showSettings = false;
        for (int i = 0; i < 1; i++) {
            EmojiTabsStrip emojiTabs = new EmojiTabsStrip(context, null, false, true, type, showSettings ? () -> {
                if (dismiss != null) {
                    dismiss.run();
                }
            } : null) {

                @Override
                protected ColorFilter getEmojiColorFilter() {
                    return premiumStarColorFilter;
                }

                @Override
                protected boolean onTabClick(int index) {
                    return true;
                }

                @Override
                protected void onTabCreate(EmojiTabButton button) {
                    if (showAnimator == null || showAnimator.isRunning()) {
                        button.setScaleX(0);
                        button.setScaleY(0);
                    }
                }
            };
            emojiTabs.recentTab.setOnLongClickListener(e -> {
                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception ignore) {
                }
                return true;
            });
            emojiTabs.updateButtonDrawables = false;
            if (type == TYPE_AVATAR_CONSTRUCTOR) {
                emojiTabs.setAnimatedEmojiCacheType(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
            } else {
                emojiTabs.setAnimatedEmojiCacheType(type == TYPE_EMOJI_STATUS || type == TYPE_SET_DEFAULT_REACTION ? AnimatedEmojiDrawable.CACHE_TYPE_TAB_STRIP : AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_TAB_STRIP);
            }
            emojiTabs.animateAppear = bubbleX == null;
            emojiTabs.setPaddingLeft(5);
            contentView.addView(emojiTabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));

        }
        emojiTabsShadow = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (bubbleX != null) {
                    setPivotX(bubbleX);
                }
            }
        };
        emojiTabsShadow.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        contentView.addView(emojiTabsShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.TOP, 0, 36, 0, 0));

        gridViewContainer = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) + AndroidUtilities.dp(36), MeasureSpec.EXACTLY));
            }
        };
        contentView.addView(gridViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 36 + (1 / AndroidUtilities.density), 0, 0));
        bigReactionImageReceiver.setLayerNum(7);

        emojiSearchGridView = new SelectAnimatedEmojiDialogTest1.EmojiListView(context) {
            @Override
            public void onScrolled(int dx, int dy) {
                super.onScrolled(dx, dy);
            }
        };
        if (emojiSearchGridView.getItemAnimator() != null) {
            emojiSearchGridView.getItemAnimator().setDurations(180);
            emojiSearchGridView.getItemAnimator().setMoveInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        }

        emojiSearchGridView.setVisibility(View.GONE);
        gridViewContainer.addView(emojiSearchGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 0, 0));
        contentView.addView(gridViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 36 + (1 / AndroidUtilities.density), 0, 0));

    }

    private long animateExpandStartTime = -1;
    private int animateExpandFromPosition = -1, animateExpandToPosition = -1;
    private boolean smoothScrolling = false;
    private float animateExpandFromButtonTranslate;

    public class EmojiListView extends RecyclerListView {


        public EmojiListView(Context context) {
            super(context);

            setDrawSelectorBehind(true);
            setClipToPadding(false);
            setSelectorRadius(AndroidUtilities.dp(4));
            setSelectorDrawableColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));
        }
        ArrayList<SelectAnimatedEmojiDialogTest1.EmojiListView.DrawingInBackgroundLine> unusedLineDrawables = new ArrayList<>();
        ArrayList<SelectAnimatedEmojiDialogTest1.EmojiListView.DrawingInBackgroundLine> lineDrawables = new ArrayList<>();
        ArrayList<SelectAnimatedEmojiDialogTest1.EmojiListView.DrawingInBackgroundLine> lineDrawablesTmp = new ArrayList<>();
        private boolean invalidated;

        private LongSparseArray<AnimatedEmojiDrawable> animatedEmojiDrawables = new LongSparseArray<>();

        @Override
        public boolean drawChild(Canvas canvas, View child, long drawingTime) {
//            if (child instanceof ImageViewEmoji) {
//                return false;
//            }
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected boolean canHighlightChildAt(View child, float x, float y) {
            if (child instanceof SelectAnimatedEmojiDialog.ImageViewEmoji && (((SelectAnimatedEmojiDialog.ImageViewEmoji) child).empty || ((SelectAnimatedEmojiDialog.ImageViewEmoji) child).drawable instanceof AnimatedEmojiDrawable && ((AnimatedEmojiDrawable) ((SelectAnimatedEmojiDialog.ImageViewEmoji) child).drawable).canOverrideColor())) {
                setSelectorDrawableColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider), 30));
            } else {
                setSelectorDrawableColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));
            }
            return super.canHighlightChildAt(child, x, y);
        }

        private int lastChildCount = -1;

        @Override
        public void setAlpha(float alpha) {
            super.setAlpha(alpha);
            invalidate();
        }

        @Override
        public void dispatchDraw(Canvas canvas) {
            if (getVisibility() != View.VISIBLE) {
                return;
            }
            invalidated = false;
            int restoreTo = canvas.getSaveCount();

            if (!selectorRect.isEmpty()) {
                selectorDrawable.setBounds(selectorRect);
                canvas.save();
                if (selectorTransformer != null) {
                    selectorTransformer.accept(canvas);
                }
                selectorDrawable.draw(canvas);
                canvas.restore();
            }

            for (int i = 0; i < viewsGroupedByLines.size(); i++) {
                ArrayList<SelectAnimatedEmojiDialog.ImageViewEmoji> arrayList = viewsGroupedByLines.valueAt(i);
                arrayList.clear();
                unusedArrays.add(arrayList);
            }
            viewsGroupedByLines.clear();
            final boolean animatedExpandIn = animateExpandStartTime > 0 && (SystemClock.elapsedRealtime() - animateExpandStartTime) < animateExpandDuration();
            final boolean drawButton = animatedExpandIn && animateExpandFromButton != null && animateExpandFromPosition >= 0;
            if (animatedEmojiDrawables != null) {
                for (int i = 0; i < getChildCount(); ++i) {
                    View child = getChildAt(i);
                    if (child instanceof SelectAnimatedEmojiDialog.ImageViewEmoji) {
                        SelectAnimatedEmojiDialog.ImageViewEmoji imageViewEmoji = (SelectAnimatedEmojiDialog.ImageViewEmoji) child;
                        imageViewEmoji.updatePressedProgress();
                        int top = smoothScrolling ? (int) child.getY() : child.getTop();
                        ArrayList<SelectAnimatedEmojiDialog.ImageViewEmoji> arrayList = viewsGroupedByLines.get(top);

                        canvas.save();
                        canvas.translate(imageViewEmoji.getX(), imageViewEmoji.getY());
                        imageViewEmoji.drawSelected(canvas, this);
                        canvas.restore();

                        if (imageViewEmoji.getBackground() != null) {
                            imageViewEmoji.getBackground().setBounds((int) imageViewEmoji.getX(), (int) imageViewEmoji.getY(), (int) imageViewEmoji.getX() + imageViewEmoji.getWidth(), (int) imageViewEmoji.getY() + imageViewEmoji.getHeight());
                            int wasAlpha = 255; // imageViewEmoji.getBackground().getAlpha();
                            imageViewEmoji.getBackground().setAlpha((int) (wasAlpha * imageViewEmoji.getAlpha()));
                            imageViewEmoji.getBackground().draw(canvas);
                            imageViewEmoji.getBackground().setAlpha(wasAlpha);
                        }
                        arrayList.add(imageViewEmoji);
                        if (imageViewEmoji.premiumLockIconView != null && imageViewEmoji.premiumLockIconView.getVisibility() == View.VISIBLE) {
                            if (imageViewEmoji.premiumLockIconView.getImageReceiver() == null && imageViewEmoji.imageReceiverToDraw != null) {
                                imageViewEmoji.premiumLockIconView.setImageReceiver(imageViewEmoji.imageReceiverToDraw);
                            }
                        }
                    }
                    if (drawButton && child != null) {
                        int position = getChildAdapterPosition(child);
                        if (position == animateExpandFromPosition - (animateExpandFromButtonTranslate > 0 ? 0 : 1)) {
                            float t = CubicBezierInterpolator.EASE_OUT.getInterpolation(MathUtils.clamp((SystemClock.elapsedRealtime() - animateExpandStartTime) / 200f, 0, 1));
                            if (t < 1) {
                                canvas.saveLayerAlpha(child.getLeft(), child.getTop(), child.getRight(), child.getBottom(), (int) (255 * (1f - t)), Canvas.ALL_SAVE_FLAG);
                                canvas.translate(child.getLeft(), child.getTop() + animateExpandFromButtonTranslate);
                                final float scale = .5f + .5f * (1f - t);
                                canvas.scale(scale, scale, child.getWidth() / 2f, child.getHeight() / 2f);
                                animateExpandFromButton.draw(canvas);
                                canvas.restore();
                            }
                        }
                    }
                }
            }

            lineDrawablesTmp.clear();
            lineDrawablesTmp.addAll(lineDrawables);
            lineDrawables.clear();

            long time = System.currentTimeMillis();
            for (int i = 0; i < viewsGroupedByLines.size(); i++) {
                int position = getChildAdapterPosition(firstView);
                SelectAnimatedEmojiDialogTest1.EmojiListView.DrawingInBackgroundLine drawable = null;
                for (int k = 0; k < lineDrawablesTmp.size(); k++) {
                    if (lineDrawablesTmp.get(k).position == position) {
                        drawable = lineDrawablesTmp.get(k);
                        lineDrawablesTmp.remove(k);
                        break;
                    }
                }
                if (drawable == null) {
                    if (!unusedLineDrawables.isEmpty()) {
                        drawable = unusedLineDrawables.remove(unusedLineDrawables.size() - 1);
                    } else {
                        drawable = new SelectAnimatedEmojiDialogTest1.EmojiListView.DrawingInBackgroundLine();
                        drawable.setLayerNum(7);
                    }
                    drawable.position = position;
                    drawable.onAttachToWindow();
                }
                lineDrawables.add(drawable);
                canvas.restore();
            }

            for (int i = 0; i < lineDrawablesTmp.size(); i++) {
                if (unusedLineDrawables.size() < 3) {
                    unusedLineDrawables.add(lineDrawablesTmp.get(i));
                    lineDrawablesTmp.get(i).imageViewEmojis = null;
                    lineDrawablesTmp.get(i).reset();
                } else {
                    lineDrawablesTmp.get(i).onDetachFromWindow();
                }
            }
            lineDrawablesTmp.clear();

            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (child instanceof SelectAnimatedEmojiDialog.ImageViewEmoji) {
                    SelectAnimatedEmojiDialog.ImageViewEmoji imageViewEmoji = (SelectAnimatedEmojiDialog.ImageViewEmoji) child;
                    if (imageViewEmoji.premiumLockIconView != null && imageViewEmoji.premiumLockIconView.getVisibility() == View.VISIBLE) {
                        canvas.save();
                        canvas.translate(
                                (int) (imageViewEmoji.getX() + imageViewEmoji.getMeasuredWidth() - imageViewEmoji.premiumLockIconView.getMeasuredWidth()),
                                (int) (imageViewEmoji.getY() + imageViewEmoji.getMeasuredHeight() - imageViewEmoji.premiumLockIconView.getMeasuredHeight())
                        );
                        imageViewEmoji.premiumLockIconView.draw(canvas);
                        canvas.restore();
                    }
                } else if (child != null && child != animateExpandFromButton) {
                    canvas.save();
                    canvas.translate((int) child.getX(), (int) child.getY());
                    child.draw(canvas);
                    canvas.restore();
                }
            }

            canvas.restoreToCount(restoreTo);
        }

        public class DrawingInBackgroundLine extends DrawingInBackgroundThreadDrawable {

            public int position;
            public int startOffset;
            ArrayList<SelectAnimatedEmojiDialog.ImageViewEmoji> imageViewEmojis;
            ArrayList<SelectAnimatedEmojiDialog.ImageViewEmoji> drawInBackgroundViews = new ArrayList<>();
            float skewAlpha = 1f;
            boolean skewBelow = false;

            @Override
            public void draw(Canvas canvas, long time, int w, int h, float alpha) {
                if (imageViewEmojis == null) {
                    return;
                }
                skewAlpha = 1f;
                skewBelow = false;
                if (!imageViewEmojis.isEmpty()) {
                    View firstView = imageViewEmojis.get(0);
                    if (firstView.getY() > getHeight() - getPaddingBottom() - firstView.getHeight()) {
                        skewAlpha = MathUtils.clamp(-(firstView.getY() - getHeight() + getPaddingBottom()) / firstView.getHeight(), 0, 1);
                        skewAlpha = .25f + .75f * skewAlpha;
                    }
                }
                boolean drawInUi = skewAlpha < 1 || isAnimating() || imageViewEmojis.size() <= 4 || !LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS) || enterAnimationInProgress() || type == TYPE_AVATAR_CONSTRUCTOR;
                if (!drawInUi) {
                    boolean animatedExpandIn = animateExpandStartTime > 0 && (SystemClock.elapsedRealtime() - animateExpandStartTime) < animateExpandDuration();
                    for (int i = 0; i < imageViewEmojis.size(); i++) {
                        SelectAnimatedEmojiDialog.ImageViewEmoji img = imageViewEmojis.get(i);
                        if (img.pressedProgress != 0 || img.backAnimator != null || img.getTranslationX() != 0 || img.getTranslationY() != 0 || img.getAlpha() != 1 || (animatedExpandIn && img.position > animateExpandFromPosition && img.position < animateExpandToPosition) || img.isStaticIcon) {
                            drawInUi = true;
                            break;
                        }
                    }
                }
                if (drawInUi) {
                    prepareDraw(System.currentTimeMillis());
                    drawInUiThread(canvas, alpha);
                    reset();
                } else {
                    super.draw(canvas, time, w, h, alpha);
                }
            }

//            float[] verts = new float[16];

            @Override
            public void drawBitmap(Canvas canvas, Bitmap bitmap, Paint paint) {
                canvas.drawBitmap(bitmap, 0, 0, paint);
//                }
            }

            @Override
            public void prepareDraw(long time) {
                drawInBackgroundViews.clear();
                for (int i = 0; i < imageViewEmojis.size(); i++) {
                    SelectAnimatedEmojiDialog.ImageViewEmoji imageView = imageViewEmojis.get(i);
                    if (imageView.notDraw) {
                        continue;
                    }
                    ImageReceiver imageReceiver;
                    if (imageView.empty) {
                        Drawable drawable = getPremiumStar();
                        float scale = 1f;
                        if (imageView.pressedProgress != 0 || imageView.selected) {
                            scale *= 0.8f + 0.2f * (1f - (imageView.selected ? .7f : imageView.pressedProgress));
                        }
                        if (drawable == null) {
                            continue;
                        }
                        drawable.setAlpha(255);
                        int topOffset = 0; // (int) (imageView.getHeight() * .03f);
                        int w = imageView.getWidth() - imageView.getPaddingLeft() - imageView.getPaddingRight();
                        int h = imageView.getHeight() - imageView.getPaddingTop() - imageView.getPaddingBottom();
                        AndroidUtilities.rectTmp2.set(
                                (int) (imageView.getWidth() / 2f - w / 2f * imageView.getScaleX() * scale),
                                (int) (imageView.getHeight() / 2f - h / 2f * imageView.getScaleY() * scale),
                                (int) (imageView.getWidth() / 2f + w / 2f * imageView.getScaleX() * scale),
                                (int) (imageView.getHeight() / 2f + h / 2f * imageView.getScaleY() * scale)
                        );
                        AndroidUtilities.rectTmp2.offset(imageView.getLeft() - startOffset, topOffset);
                        if (imageView.drawableBounds == null) {
                            imageView.drawableBounds = new Rect();
                        }
                        imageView.drawableBounds.set(AndroidUtilities.rectTmp2);
                        imageView.setDrawable(drawable);
                        drawInBackgroundViews.add(imageView);
                    } else {
                        float scale = 1, alpha = 1;
                        if (imageView.pressedProgress != 0 || imageView.selected) {
                            scale *= 0.8f + 0.2f * (1f - (imageView.selected ? .7f : imageView.pressedProgress));
                        }
                        boolean animatedExpandIn = animateExpandStartTime > 0 && (SystemClock.elapsedRealtime() - animateExpandStartTime) < animateExpandDuration();
                        if (animatedExpandIn && animateExpandFromPosition >= 0 && animateExpandToPosition >= 0 && animateExpandStartTime > 0) {
                            int position = getChildAdapterPosition(imageView);
                            final int pos = position - animateExpandFromPosition;
                            final int count = animateExpandToPosition - animateExpandFromPosition;
                            if (pos >= 0 && pos < count) {
                                final float appearDuration = animateExpandAppearDuration();
                                final float AppearT = (MathUtils.clamp((SystemClock.elapsedRealtime() - animateExpandStartTime) / appearDuration, 0, 1));
                                final float alphaT = AndroidUtilities.cascade(AppearT, pos, count, count / 4f);
                                final float scaleT = AndroidUtilities.cascade(AppearT, pos, count, count / 4f);
                                scale *= .5f + appearScaleInterpolator.getInterpolation(scaleT) * .5f;
                                alpha *= alphaT;
                            }
                        } else {
                            alpha *= imageView.getAlpha();
                        }

                        if (!imageView.isDefaultReaction && !imageView.isStaticIcon) {
                            AnimatedEmojiSpan span = imageView.span;
                            if (span == null) {
                                continue;
                            }
                            AnimatedEmojiDrawable drawable = null;
                            if (imageView.drawable instanceof AnimatedEmojiDrawable) {
                                drawable = (AnimatedEmojiDrawable) imageView.drawable;
                            }

                            if (drawable == null || drawable.getImageReceiver() == null) {
                                continue;
                            }
                            imageReceiver = drawable.getImageReceiver();
                            drawable.setAlpha((int) (255 * alpha));
                            imageView.setDrawable(drawable);
                            imageView.drawable.setColorFilter(premiumStarColorFilter);
                        } else {
                            imageReceiver = imageView.imageReceiver;
                            imageReceiver.setAlpha(alpha);
                        }
                        if (imageReceiver == null) {
                            continue;
                        }

                        if (imageView.selected) {
                            imageReceiver.setRoundRadius(AndroidUtilities.dp(4));
                        } else {
                            imageReceiver.setRoundRadius(0);
                        }
                        imageView.backgroundThreadDrawHolder[threadIndex] = imageReceiver.setDrawInBackgroundThread(imageView.backgroundThreadDrawHolder[threadIndex], threadIndex);
                        imageView.backgroundThreadDrawHolder[threadIndex].time = time;
                        imageView.imageReceiverToDraw = imageReceiver;

                        imageView.update(time);

                        int topOffset = 0; // (int) (imageView.getHeight() * .03f);
                        int w = imageView.getWidth() - imageView.getPaddingLeft() - imageView.getPaddingRight();
                        int h = imageView.getHeight() - imageView.getPaddingTop() - imageView.getPaddingBottom();
                        AndroidUtilities.rectTmp2.set(imageView.getPaddingLeft(), imageView.getPaddingTop(), imageView.getWidth() - imageView.getPaddingRight(), imageView.getHeight() - imageView.getPaddingBottom());
                        if (imageView.selected && type != TYPE_TOPIC_ICON && type != TYPE_AVATAR_CONSTRUCTOR) {
                            AndroidUtilities.rectTmp2.set(
                                    (int) Math.round(AndroidUtilities.rectTmp2.centerX() - AndroidUtilities.rectTmp2.width() / 2f * 0.86f),
                                    (int) Math.round(AndroidUtilities.rectTmp2.centerY() - AndroidUtilities.rectTmp2.height() / 2f * 0.86f),
                                    (int) Math.round(AndroidUtilities.rectTmp2.centerX() + AndroidUtilities.rectTmp2.width() / 2f * 0.86f),
                                    (int) Math.round(AndroidUtilities.rectTmp2.centerY() + AndroidUtilities.rectTmp2.height() / 2f * 0.86f)
                            );
                        }
                        AndroidUtilities.rectTmp2.offset(imageView.getLeft() + (int) imageView.getTranslationX() - startOffset, topOffset);
                        imageView.backgroundThreadDrawHolder[threadIndex].setBounds(AndroidUtilities.rectTmp2);

                        imageView.skewAlpha = 1f;
                        imageView.skewIndex = i;

                        drawInBackgroundViews.add(imageView);
                    }
                }
            }

            @Override
            public void drawInBackground(Canvas canvas) {
                for (int i = 0; i < drawInBackgroundViews.size(); i++) {
                    SelectAnimatedEmojiDialog.ImageViewEmoji imageView = drawInBackgroundViews.get(i);
                    if (!imageView.notDraw) {
                        if (imageView.empty) {
                            imageView.drawable.setBounds(imageView.drawableBounds);
                            imageView.drawable.draw(canvas);
                        } else if (imageView.imageReceiverToDraw != null) {
//                            imageView.drawable.setColorFilter(premiumStarColorFilter);
                            imageView.imageReceiverToDraw.draw(canvas, imageView.backgroundThreadDrawHolder[threadIndex]);
                        }
                    }
                }
            }

            private OvershootInterpolator appearScaleInterpolator = new OvershootInterpolator(3f);

            @Override
            protected void drawInUiThread(Canvas canvas, float alpha) {
                if (imageViewEmojis != null) {
                    canvas.save();
                    canvas.translate(-startOffset, 0);
                    for (int i = 0; i < imageViewEmojis.size(); i++) {
                        SelectAnimatedEmojiDialog.ImageViewEmoji imageView = imageViewEmojis.get(i);
                        if (imageView.notDraw) {
                            continue;
                        }

                        float scale = imageView.getScaleX();
                        if (imageView.pressedProgress != 0 || imageView.selected) {
                            scale *= 0.8f + 0.2f * (1f - ((imageView.selected && type != TYPE_TOPIC_ICON && type != TYPE_AVATAR_CONSTRUCTOR) ? 0.7f : imageView.pressedProgress));
                        }
                        boolean animatedExpandIn = animateExpandStartTime > 0 && (SystemClock.elapsedRealtime() - animateExpandStartTime) < animateExpandDuration();
                        boolean animatedExpandInLocal = animatedExpandIn && animateExpandFromPosition >= 0 && animateExpandToPosition >= 0 && animateExpandStartTime > 0;
                        if (animatedExpandInLocal) {
                            int position = getChildAdapterPosition(imageView);
                            final int pos = position - animateExpandFromPosition;
                            final int count = animateExpandToPosition - animateExpandFromPosition;
                            if (pos >= 0 && pos < count) {
                                final float appearDuration = animateExpandAppearDuration();
                                final float AppearT = (MathUtils.clamp((SystemClock.elapsedRealtime() - animateExpandStartTime) / appearDuration, 0, 1));
                                final float alphaT = AndroidUtilities.cascade(AppearT, pos, count, count / 4f);
                                final float scaleT = AndroidUtilities.cascade(AppearT, pos, count, count / 4f);
                                scale *= .5f + appearScaleInterpolator.getInterpolation(scaleT) * .5f;
                                alpha = alphaT;
                            }
                        } else {
                            alpha *= imageView.getAlpha();
                        }

                        AndroidUtilities.rectTmp2.set((int) imageView.getX() + imageView.getPaddingLeft(), imageView.getPaddingTop(), (int) imageView.getX() + imageView.getWidth() - imageView.getPaddingRight(), imageView.getHeight() - imageView.getPaddingBottom());
                        if (!smoothScrolling && !animatedExpandIn) {
                            AndroidUtilities.rectTmp2.offset(0, (int) imageView.getTranslationY());
                        }
                        Drawable drawable = null;
                        if (imageView.empty) {
                            drawable = getPremiumStar();
                            drawable.setBounds(AndroidUtilities.rectTmp2);
                            drawable.setAlpha(255);
                        } else if (!imageView.isDefaultReaction && !imageView.isStaticIcon) {
                            AnimatedEmojiSpan span = imageView.span;
                            if (span == null || imageView.notDraw) {
                                continue;
                            }
                            drawable = imageView.drawable;
                            if (drawable == null) {
                                continue;
                            }
                            drawable.setAlpha(255);
                            drawable.setBounds(AndroidUtilities.rectTmp2);
                        } else if (imageView.imageReceiver != null) {
                            imageView.imageReceiver.setImageCoords(AndroidUtilities.rectTmp2);
                        }
                        if (premiumStarColorFilter != null && imageView.drawable instanceof AnimatedEmojiDrawable) {
                            imageView.drawable.setColorFilter(premiumStarColorFilter);
                        }
                        imageView.skewAlpha = skewAlpha;
                        imageView.skewIndex = i;
                        if (scale != 1 || skewAlpha < 1) {
                            canvas.save();
                            canvas.scale(scale, scale, AndroidUtilities.rectTmp2.centerX(), AndroidUtilities.rectTmp2.centerY());
                            skew(canvas, i, imageView.getHeight());
                            drawImage(canvas, drawable, imageView, alpha);
                            canvas.restore();
                        } else {
                            drawImage(canvas, drawable, imageView, alpha);
                        }
                    }
                    canvas.restore();
                }
            }

            private void skew(Canvas canvas, int i, int h) {
                if (skewAlpha < 1) {
                    if (skewBelow) {
                        canvas.translate(0, h);
                        canvas.skew((1f - 2f * i / imageViewEmojis.size()) * -(1f - skewAlpha), 0);
                        canvas.translate(0, -h);
                    } else {
                        canvas.scale(1f, skewAlpha, 0, 0);
                        canvas.skew((1f - 2f * i / imageViewEmojis.size()) * (1f - skewAlpha), 0);
                    }
                }
            }

            private void drawImage(Canvas canvas, Drawable drawable, SelectAnimatedEmojiDialog.ImageViewEmoji imageView, float alpha) {
                if (drawable != null) {
                    drawable.setAlpha((int) (255 * alpha));
                    drawable.draw(canvas);
                    drawable.setColorFilter(premiumStarColorFilter);
                } else if ((imageView.isDefaultReaction || imageView.isStaticIcon) && imageView.imageReceiver != null) {
                    canvas.save();
                    canvas.clipRect(imageView.imageReceiver.getImageX(), imageView.imageReceiver.getImageY(), imageView.imageReceiver.getImageX2(), imageView.imageReceiver.getImageY2());
                    imageView.imageReceiver.setAlpha(alpha);
                    imageView.imageReceiver.draw(canvas);
                    canvas.restore();
                }
            }

            @Override
            public void onFrameReady() {
                super.onFrameReady();
                for (int i = 0; i < drawInBackgroundViews.size(); i++) {
                    SelectAnimatedEmojiDialog.ImageViewEmoji imageView = drawInBackgroundViews.get(i);
                    if (imageView.backgroundThreadDrawHolder[threadIndex] != null) {
                        imageView.backgroundThreadDrawHolder[threadIndex].release();
                    }
                }
                emojiGridView.invalidate();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (this == emojiGridView) {
                bigReactionImageReceiver.onAttachedToWindow();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (this == emojiGridView) {
                bigReactionImageReceiver.onDetachedFromWindow();
            }
            release(unusedLineDrawables);
            release(lineDrawables);
            release(lineDrawablesTmp);
        }

        private void release(ArrayList<SelectAnimatedEmojiDialog.EmojiListView.DrawingInBackgroundLine> lineDrawables) {
            for (int i = 0; i < lineDrawables.size(); i++) {
                lineDrawables.get(i).onDetachFromWindow();
            }
            lineDrawables.clear();
        }

        @Override
        public void invalidate() {
            if (invalidated) {
                return;
            }
            invalidated = true;
            super.invalidate();
        }
    }

    private ColorFilter premiumStarColorFilter;
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
    }

    public void expand(int position, View expandButton) {
        int index = positionToExpand.get(position);
        Integer from = null, count = null;
        if (index >= 0 && index < packs.size()) {
            EmojiView.EmojiPack pack = packs.get(index);
            if (pack.expanded) {
                return;
            }
        } else {
            return;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (drawBackground && type != TYPE_TOPIC_ICON && type != TYPE_AVATAR_CONSTRUCTOR) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec((int) Math.min(AndroidUtilities.dp(340 - 16), AndroidUtilities.displaySize.x * .95f), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((int) Math.min(AndroidUtilities.dp(410 - 16 - 64), AndroidUtilities.displaySize.y * .75f), MeasureSpec.AT_MOST)
            );
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public class EmojiListView extends RecyclerListView {


        public EmojiListView(Context context) {
            super(context);

            setDrawSelectorBehind(true);
            setClipToPadding(false);
            setSelectorRadius(AndroidUtilities.dp(4));
            setSelectorDrawableColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));
        }
        ArrayList<DrawingInBackgroundLine> unusedLineDrawables = new ArrayList<>();
        ArrayList<DrawingInBackgroundLine> lineDrawables = new ArrayList<>();
        ArrayList<DrawingInBackgroundLine> lineDrawablesTmp = new ArrayList<>();
        private boolean invalidated;

        private LongSparseArray<AnimatedEmojiDrawable> animatedEmojiDrawables = new LongSparseArray<>();

        @Override
        public boolean drawChild(Canvas canvas, View child, long drawingTime) {
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected boolean canHighlightChildAt(View child, float x, float y) {
            return super.canHighlightChildAt(child, x, y);
        }

        @Override
        public void setAlpha(float alpha) {
            super.setAlpha(alpha);
            invalidate();
        }

        @Override
        public void dispatchDraw(Canvas canvas) {
            if (getVisibility() != View.VISIBLE) {
                return;
            }
            invalidated = false;
            int restoreTo = canvas.getSaveCount();

            if (!selectorRect.isEmpty()) {
                selectorDrawable.setBounds(selectorRect);
                canvas.save();
                if (selectorTransformer != null) {
                    selectorTransformer.accept(canvas);
                }
                selectorDrawable.draw(canvas);
                canvas.restore();
            }

            lineDrawablesTmp.clear();
            lineDrawablesTmp.addAll(lineDrawables);
            lineDrawables.clear();

            for (int i = 0; i < lineDrawablesTmp.size(); i++) {
                if (unusedLineDrawables.size() < 3) {
                    unusedLineDrawables.add(lineDrawablesTmp.get(i));
                    lineDrawablesTmp.get(i).reset();
                } else {
                    lineDrawablesTmp.get(i).onDetachFromWindow();
                }
            }
            lineDrawablesTmp.clear();

            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
            }

            canvas.restoreToCount(restoreTo);
        }

        public class DrawingInBackgroundLine extends DrawingInBackgroundThreadDrawable {

            public int position;
            public int startOffset;
            float skewAlpha = 1f;
            boolean skewBelow = false;

            @Override
            public void draw(Canvas canvas, long time, int w, int h, float alpha) {
                skewAlpha = 1f;
                skewBelow = false;

                    super.draw(canvas, time, w, h, alpha);
            }

            @Override
            public void drawBitmap(Canvas canvas, Bitmap bitmap, Paint paint) {
                canvas.drawBitmap(bitmap, 0, 0, paint);
            }

            @Override
            public void prepareDraw(long time) {

            }

            @Override
            public void drawInBackground(Canvas canvas) {
            }
            @Override
            protected void drawInUiThread(Canvas canvas, float alpha) {
            }
            @Override
            public void onFrameReady() {
                super.onFrameReady();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            release(unusedLineDrawables);
            release(lineDrawables);
            release(lineDrawablesTmp);
        }

        private void release(ArrayList<DrawingInBackgroundLine> lineDrawables) {
            for (int i = 0; i < lineDrawables.size(); i++) {
                lineDrawables.get(i).onDetachFromWindow();
            }
            lineDrawables.clear();
        }

        @Override
        public void invalidate() {
            if (invalidated) {
                return;
            }
            invalidated = true;
            super.invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.featuredEmojiDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recentEmojiStatusesUpdate);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.featuredEmojiDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recentEmojiStatusesUpdate);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupStickersDidLoad);

    }
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad) {
            if (((int) args[0]) == MediaDataController.TYPE_EMOJIPACKS || (((int) args[0]) == MediaDataController.TYPE_IMAGE)) {

            }
        } else if (id == NotificationCenter.featuredEmojiDidLoad) {
            NotificationCenter.getGlobalInstance().doOnIdle(() -> {
                AndroidUtilities.runOnUIThread(() -> {
                }, 120);
            });
        } else if (id == NotificationCenter.recentEmojiStatusesUpdate) {
            NotificationCenter.getGlobalInstance().doOnIdle(() -> {
                AndroidUtilities.runOnUIThread(() -> {
                }, 120);
            });
        } else if (id == NotificationCenter.groupStickersDidLoad) {
        }
    }

    private Runnable dismiss;
    final float durationScale = 1f;
    final long showDuration = (long) (800 * durationScale);
    private ValueAnimator showAnimator;
    private ValueAnimator hideAnimator;
    private int animationIndex = -1;

    public void onShow(Runnable dismiss) {
        this.dismiss = dismiss;
        if (!drawBackground) {
            return;
        }
        if (showAnimator != null) {
            showAnimator.cancel();
            showAnimator = null;
        }
        if (hideAnimator != null) {
            hideAnimator.cancel();
            hideAnimator = null;
        }
        boolean animated = type != TYPE_TOPIC_ICON && type != TYPE_AVATAR_CONSTRUCTOR;

        if (animated) {
            showAnimator = ValueAnimator.ofFloat(0, 1);
            showAnimator.addUpdateListener(anm -> {
                final float t = (float) anm.getAnimatedValue();
                updateShow(t);
            });
            showAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                    NotificationCenter.getGlobalInstance().onAnimationFinish(animationIndex);
                    AndroidUtilities.runOnUIThread(NotificationCenter.getGlobalInstance()::runDelayedNotifications);
                    updateShow(1);
                }
            });
            updateShow(0);
            showAnimator.setDuration(showDuration);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
            animationIndex = NotificationCenter.getGlobalInstance().setAnimationInProgress(animationIndex, null);
            showAnimator.start();
        } else {
            updateShow(1);
        }
    }

    private void updateShow(float t) {
        if (bubble1View != null) {
            float bubble1t = MathUtils.clamp((t * showDuration - 0) / 120 / durationScale, 0, 1);
            bubble1t = CubicBezierInterpolator.EASE_OUT.getInterpolation(bubble1t);
            bubble1View.setAlpha(bubble1t);
            bubble1View.setScaleX(bubble1t);
            bubble1View.setScaleY(bubble1t);
        }

        if (bubble2View != null) {
            float bubble2t = MathUtils.clamp((t * showDuration - 30) / 120 / durationScale, 0, 1);
//            bubble2t = CubicBezierInterpolator.EASE_OUT.getInterpolation(bubble2t);
            bubble2View.setAlpha(bubble2t);
            bubble2View.setScaleX(bubble2t);
            bubble2View.setScaleY(bubble2t);
        }

        float containerx = MathUtils.clamp((t * showDuration - 40) / 700, 0, 1);
        float containery = MathUtils.clamp((t * showDuration - 80) / 700, 0, 1);
        float containeralphat = MathUtils.clamp((t * showDuration - 30) / 120, 0, 1);
        containerx = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(containerx);
        containery = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(containery);
        contentView.setAlpha(containeralphat);
        contentView.setTranslationY(AndroidUtilities.dp(-5) * (1f - containeralphat));
        if (bubble2View != null) {
            bubble2View.setTranslationY(AndroidUtilities.dp(-5) * (1f - containeralphat));
        }
        this.scaleX = .15f + .85f * containerx;
        this.scaleY = .075f + .925f * containery;
        if (bubble2View != null) {
            bubble2View.setAlpha(containeralphat);
        }
        contentView.invalidate();
    }

    public void onDismiss(Runnable dismiss) {

        if (hideAnimator != null) {
            hideAnimator.cancel();
            hideAnimator = null;
        }
        hideAnimator = ValueAnimator.ofFloat(0, 1);
        hideAnimator.addUpdateListener(anm -> {
            float t = 1f - (float) anm.getAnimatedValue();
            setTranslationY(AndroidUtilities.dp(8) * (1f - t));
            if (bubble1View != null) {
                bubble1View.setAlpha(t);
            }
            if (bubble2View != null) {
                bubble2View.setAlpha(t * t);
            }
            contentView.setAlpha(t);
            contentView.invalidate();
            invalidate();
        });
        hideAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                dismiss.run();
            }
        });
        hideAnimator.setDuration(200);
        hideAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        hideAnimator.start();
    }

    public void setDrawBackground(boolean drawBackground) {
        this.drawBackground = drawBackground;
    }



    @Override
    public void setPressed(boolean pressed) {
        return;
    }
}
