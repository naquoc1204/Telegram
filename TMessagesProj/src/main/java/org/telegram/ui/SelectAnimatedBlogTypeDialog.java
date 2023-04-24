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
import android.graphics.drawable.Drawable;
import android.util.LongSparseArray;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.DrawingInBackgroundThreadDrawable;
import org.telegram.ui.Components.EmojiTabsStrip;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class SelectAnimatedBlogTypeDialog extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

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
            if (getContentView() instanceof SelectAnimatedBlogTypeDialog) {
                ((SelectAnimatedBlogTypeDialog) getContentView()).onShow(this::dismiss);
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
            if (getContentView() instanceof SelectAnimatedBlogTypeDialog) {
                ((SelectAnimatedBlogTypeDialog) getContentView()).onDismiss(super::dismiss);
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

    public SelectAnimatedBlogTypeDialog(BaseFragment baseFragment, Context context, Integer emojiX, int type, Theme.ResourcesProvider resourcesProvider) {
        this(baseFragment, context, emojiX, type, resourcesProvider, 16);
    }

    public SelectAnimatedBlogTypeDialog(BaseFragment baseFragment, Context context, Integer emojiX, int type, Theme.ResourcesProvider resourcesProvider, int topPaddingDp) {
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


    }

    private long animateExpandStartTime = -1;
    private int animateExpandFromPosition = -1, animateExpandToPosition = -1;
    private boolean smoothScrolling = false;
    private float animateExpandFromButtonTranslate;


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
