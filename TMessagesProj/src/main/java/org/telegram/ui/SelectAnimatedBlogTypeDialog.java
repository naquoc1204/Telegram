package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.DrawingInBackgroundThreadDrawable;
import org.telegram.ui.Components.EmojiTabsStrip;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumLockIconView;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.lang.reflect.Field;
import java.util.ArrayList;

public class SelectAnimatedBlogTypeDialog extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public final static int TYPE_EMOJI_STATUS = 0;
    public final static int TYPE_REACTIONS = 1;
    public final static int TYPE_SET_DEFAULT_REACTION = 2;
    public static final int TYPE_TOPIC_ICON = 3;
    public static final int TYPE_AVATAR_CONSTRUCTOR = 4;
    private final int SPAN_COUNT_FOR_EMOJI = 8;
    private final int SPAN_COUNT = 40;
    public Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public Paint selectorAccentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean showStickers;
    public boolean forUser;
    public void setForUser(boolean forUser) {
        this.forUser = forUser;
    }

    @SuppressLint("SoonBlockedPrivateApi")
    public static class SelectAnimatedBlogTypeDialogWindow extends PopupWindow {
        private static final Field superListenerField;
        private ViewTreeObserver.OnScrollChangedListener mSuperScrollListener;
        private ViewTreeObserver mViewTreeObserver;
        private static final ViewTreeObserver.OnScrollChangedListener NOP = () -> {
            /* do nothing */
        };

        static {
            Field f = null;
            try {
                f = PopupWindow.class.getDeclaredField("mOnScrollChangedListener");
                f.setAccessible(true);
            } catch (NoSuchFieldException e) {
                /* ignored */
            }
            superListenerField = f;
        }

        public SelectAnimatedBlogTypeDialogWindow(View anchor, int width, int height) {
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
            if (superListenerField != null) {
                try {
                    mSuperScrollListener = (ViewTreeObserver.OnScrollChangedListener) superListenerField.get(this);
                    superListenerField.set(this, NOP);
                } catch (Exception e) {
                    mSuperScrollListener = null;
                }
            }
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
    private EmojiTabsStrip[] cachedEmojiTabs = new EmojiTabsStrip[2];
    public EmojiTabsStrip emojiTabs;
    private View emojiTabsShadow;
    public FrameLayout gridViewContainer;
    public EmojiListView emojiGridView;
    public EmojiListView emojiSearchGridView;
    private View bubble1View;
    private View bubble2View;
    private View topGradientView;
    private View bottomGradientView;
//    private Adapter adapter;
    private GridLayoutManager layoutManager;
    private RecyclerAnimationScrollHelper scrollHelper;
    private View contentViewForeground;
    private SparseIntArray sectionToPosition = new SparseIntArray();
    private SparseIntArray positionToExpand = new SparseIntArray();
    private ArrayList<Long> expandedEmojiSets = new ArrayList<>();
    private ArrayList<TLRPC.TL_messages_stickerSet> frozenEmojiPacks = new ArrayList<>();
    private ArrayList<EmojiView.EmojiPack> packs = new ArrayList<>();
    private boolean drawBackground = true;
    ImageReceiver bigReactionImageReceiver = new ImageReceiver();
    private SelectStatusDurationDialog selectStatusDateDialog;

    private Integer emojiX;

    private float scaleX, scaleY;

    private int topMarginDp;
    DefaultItemAnimator emojiItemAnimator;

    public SelectAnimatedBlogTypeDialog(BaseFragment baseFragment, Context context, boolean includeEmpty, Integer emojiX, int type, Theme.ResourcesProvider resourcesProvider) {
        this(baseFragment, context, includeEmpty, emojiX, type, resourcesProvider, 16);
    }

    public SelectAnimatedBlogTypeDialog(BaseFragment baseFragment, Context context, boolean includeEmpty, Integer emojiX, int type, Theme.ResourcesProvider resourcesProvider, int topPaddingDp) {
        super(context);
        this.type = type;
        selectorPaint.setColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));
        selectorAccentPaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider), 30));
        premiumStarColorFilter = new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider), PorterDuff.Mode.SRC_IN);

        this.emojiX = emojiX;
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

        boolean showSettings = baseFragment != null && type != TYPE_TOPIC_ICON && type != TYPE_AVATAR_CONSTRUCTOR;
        for (int i = 0; i < 2; i++) {
            EmojiTabsStrip emojiTabs = new EmojiTabsStrip(context, null, false, true, type, showSettings ? () -> {
                baseFragment.presentFragment(new StickersActivity(MediaDataController.TYPE_EMOJIPACKS, frozenEmojiPacks));
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
                    if (smoothScrolling) {
                        return false;
                    }
                    if (type == TYPE_AVATAR_CONSTRUCTOR) {
                        if (index == 0) {
                            showStickers = !showStickers;
                            SelectAnimatedBlogTypeDialog.this.emojiTabs.setVisibility(View.GONE);
                            SelectAnimatedBlogTypeDialog.this.emojiTabs = cachedEmojiTabs[showStickers ? 1 : 0];
                            SelectAnimatedBlogTypeDialog.this.emojiTabs.setVisibility(View.VISIBLE);
                            SelectAnimatedBlogTypeDialog.this.emojiTabs.toggleEmojiStickersTab.setDrawable(ContextCompat.getDrawable(getContext(), showStickers ? R.drawable.msg_emoji_stickers : R.drawable.msg_emoji_smiles));
                            layoutManager.scrollToPositionWithOffset(0, 0);
                            return true;
                        }
                        index--;
                    }
                    int position = 0;
                    if (index > 0 && sectionToPosition.indexOfKey(index - 1) >= 0) {
                        position = sectionToPosition.get(index - 1);
                    }
                    scrollToPosition(position, AndroidUtilities.dp(-2));
                    SelectAnimatedBlogTypeDialog.this.emojiTabs.select(index);
                    emojiGridView.scrolledByUserOnce = true;
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
            emojiTabs.updateButtonDrawables = false;
            if (type == TYPE_AVATAR_CONSTRUCTOR) {
                emojiTabs.setAnimatedEmojiCacheType(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
            } else {
                emojiTabs.setAnimatedEmojiCacheType(type == TYPE_EMOJI_STATUS || type == TYPE_SET_DEFAULT_REACTION ? AnimatedEmojiDrawable.CACHE_TYPE_TAB_STRIP : AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_TAB_STRIP);
            }
            emojiTabs.animateAppear = bubbleX == null;
            emojiTabs.setPaddingLeft(5);
            contentView.addView(emojiTabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));
            cachedEmojiTabs[i] = emojiTabs;
        }

        emojiTabs = cachedEmojiTabs[0];
        cachedEmojiTabs[1].setVisibility(View.GONE);

        emojiTabsShadow = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (bubbleX != null) {
                    setPivotX(bubbleX);
                }
            }
        };
        contentView.addView(emojiTabsShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.TOP, 0, 36, 0, 0));
        AndroidUtilities.updateViewVisibilityAnimated(emojiTabsShadow, true, 1f, false);
        gridViewContainer = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) + AndroidUtilities.dp(36), MeasureSpec.EXACTLY));
            }
        };
        gridViewContainer.addView(emojiGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 0, 0));

        emojiSearchGridView = new EmojiListView(context) {
            @Override
            public void onScrolled(int dx, int dy) {
                super.onScrolled(dx, dy);
            }
        };
        if (emojiSearchGridView.getItemAnimator() != null) {
            emojiSearchGridView.getItemAnimator().setDurations(180);
            emojiSearchGridView.getItemAnimator().setMoveInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        }
        TextView emptyViewText = new TextView(context);
        if (type == TYPE_AVATAR_CONSTRUCTOR) {
            emptyViewText.setText(LocaleController.getString("NoEmojiOrStickersFound", R.string.NoEmojiOrStickersFound));
        } else if (type == TYPE_EMOJI_STATUS) {
            emptyViewText.setText(LocaleController.getString("NoEmojiFound", R.string.NoEmojiFound));
        } else if (type == TYPE_REACTIONS || type == TYPE_SET_DEFAULT_REACTION) {
            emptyViewText.setText(LocaleController.getString("NoReactionsFound", R.string.NoReactionsFound));
        } else {
            emptyViewText.setText(LocaleController.getString("NoIconsFound", R.string.NoIconsFound));
        }

        emptyViewText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyViewText.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelEmptyText, resourcesProvider));

        emojiSearchGridView.setVisibility(View.GONE);
        gridViewContainer.addView(emojiSearchGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 0, 0));
        contentView.addView(gridViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 36 + (1 / AndroidUtilities.density), 0, 0));

        scrollHelper = new RecyclerAnimationScrollHelper(emojiGridView, layoutManager);
        scrollHelper.setAnimationCallback(new RecyclerAnimationScrollHelper.AnimationCallback() {
            @Override
            public void onPreAnimation() {
                smoothScrolling = true;
            }

            @Override
            public void onEndAnimation() {
                smoothScrolling = false;
            }
        });

        RecyclerListView.OnItemLongClickListenerExtended onItemLongClick = new RecyclerListView.OnItemLongClickListenerExtended() {
            @Override
            public boolean onItemClick(View view, int position, float x, float y) {
                return false;
            }

            @Override
            public void onLongClickRelease() {
            }
        };
        emojiGridView.setOnItemLongClickListener(onItemLongClick, (long) (ViewConfiguration.getLongPressTimeout() * 0.25f));
        emojiSearchGridView.setOnItemLongClickListener(onItemLongClick, (long) (ViewConfiguration.getLongPressTimeout() * 0.25f));
        RecyclerListView.OnItemClickListener onItemClick = (view, position) -> {
            if (view instanceof ImageViewEmoji) {
                ImageViewEmoji viewEmoji = (ImageViewEmoji) view;
                if (viewEmoji.isDefaultReaction) {
                    incrementHintUse();
                }
                if (type != TYPE_REACTIONS) {
                    try {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    } catch (Exception ignore) {}
                }
            } else if (view instanceof ImageView) {
                if (type != TYPE_REACTIONS) {
                    try {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    } catch (Exception ignore) {}
                }
            } else if (view instanceof EmojiPackExpand) {
                EmojiPackExpand button = (EmojiPackExpand) view;
                expand(position, button);
                if (type != TYPE_REACTIONS) {
                    try {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    } catch (Exception ignore) {}
                }
            } else if (view != null) {
                view.callOnClick();
            }
        };
        emojiGridView.setOnItemClickListener(onItemClick);
        emojiSearchGridView.setOnItemClickListener(onItemClick);

//        searchBox = new SearchBox(context);
//        searchBox.setTranslationY(-AndroidUtilities.dp( 52));
//        searchBox.setVisibility(View.INVISIBLE);
//        gridViewContainer.addView(searchBox, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52, Gravity.TOP, 0, -4, 0, 0));

        topGradientView = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (bubbleX != null) {
                    setPivotX(bubbleX);
                }
            }
        };
        Drawable topGradient = getResources().getDrawable(R.drawable.gradient_top);
        topGradient.setColorFilter(new PorterDuffColorFilter(AndroidUtilities.multiplyAlphaComponent(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider), .8f), PorterDuff.Mode.SRC_IN));
        topGradientView.setBackground(topGradient);
        topGradientView.setAlpha(0);
        contentView.addView(topGradientView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 36 + 1f / AndroidUtilities.density, 0, 0));

        bottomGradientView = new View(context);
        Drawable bottomGradient = getResources().getDrawable(R.drawable.gradient_bottom);
        bottomGradient.setColorFilter(new PorterDuffColorFilter(AndroidUtilities.multiplyAlphaComponent(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider), .8f), PorterDuff.Mode.SRC_IN));
        bottomGradientView.setBackground(bottomGradient);
        bottomGradientView.setAlpha(0);
        contentView.addView(bottomGradientView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        contentViewForeground = new View(context);
        contentViewForeground.setAlpha(0);
        contentViewForeground.setBackgroundColor(0xff000000);
        contentView.addView(contentViewForeground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

//        preload(type, currentAccount);

        bigReactionImageReceiver.setLayerNum(7);
    }
    private ColorFilter premiumStarColorFilter;


    private float scrimAlpha = 1f;
    private Rect drawableToBounds;

    private View scrimDrawableParent;

    private SelectAnimatedEmojiDialog.ImageViewEmoji emojiSelectView;
    private Rect emojiSelectRect;
    private float emojiSelectAlpha = 1f;
    private Theme.ResourcesProvider resourcesProvider;
    private int scrimColor;
    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable scrimDrawable;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (scrimDrawable != null && emojiX != null) {
//            Rect bounds = (scrimDrawableBounds == null ? scrimDrawableBounds = scrimDrawable.getBounds() : scrimDrawableBounds);
            Rect bounds = scrimDrawable.getBounds();
            float scale = scrimDrawableParent == null ? 1f : scrimDrawableParent.getScaleY();
            int wasAlpha = 255;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                wasAlpha = scrimDrawable.getAlpha();
            }
            int h = (scrimDrawableParent == null ? bounds.height() : scrimDrawableParent.getHeight());
            canvas.save();
            canvas.translate(0, -getTranslationY());
            scrimDrawable.setAlpha((int) (wasAlpha * Math.pow(contentView.getAlpha(), .25f) * scrimAlpha));
            if (drawableToBounds == null) {
                drawableToBounds = new Rect();
            }
            drawableToBounds.set(
                    (int) (bounds.centerX() - bounds.width() / 2f * scale - bounds.centerX() + emojiX + (scale > 1f && scale < 1.5f ? 2 : 0)),
                    (int) ((h - (h - bounds.bottom)) * scale - (scale > 1.5f ? (bounds.height() * .81f + 1) : 0) - bounds.top - bounds.height() / 2f + AndroidUtilities.dp(topMarginDp) - bounds.height() * scale),
                    (int) (bounds.centerX() + bounds.width() / 2f * scale - bounds.centerX() + emojiX + (scale > 1f && scale < 1.5f ? 2 : 0)),
                    (int) ((h - (h - bounds.bottom)) * scale - (scale > 1.5f ? bounds.height() * .81f + 1 : 0) - bounds.top - bounds.height() / 2f + AndroidUtilities.dp(topMarginDp))
            );
            scrimDrawable.setBounds(
                    drawableToBounds.left,
                    drawableToBounds.top,
                    (int) (drawableToBounds.left + drawableToBounds.width() / scale),
                    (int) (drawableToBounds.top + drawableToBounds.height() / scale)
            );
            canvas.scale(scale, scale, drawableToBounds.left, drawableToBounds.top);
            scrimDrawable.draw(canvas);
            scrimDrawable.setAlpha(wasAlpha);
            scrimDrawable.setBounds(bounds);
            canvas.restore();
        }
        super.dispatchDraw(canvas);
        if (emojiSelectView != null && emojiSelectRect != null && drawableToBounds != null && emojiSelectView.drawable != null) {
            canvas.save();
            canvas.translate(0, -getTranslationY());
            emojiSelectView.drawable.setAlpha((int) (255 * emojiSelectAlpha));
            emojiSelectView.drawable.setBounds(emojiSelectRect);
            emojiSelectView.drawable.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider), scrimColor, 1f - scrimAlpha), PorterDuff.Mode.SRC_IN));
            emojiSelectView.drawable.draw(canvas);
            canvas.restore();
        }
    }

    private boolean smoothScrolling = false;

    private void scrollToPosition(int p, int offset) {
        View view = layoutManager.findViewByPosition(p);
        int firstPosition = layoutManager.findFirstVisibleItemPosition();
        if ((view == null && Math.abs(p - firstPosition) > SPAN_COUNT_FOR_EMOJI * 9f) || !SharedConfig.animationsEnabled()) {
            scrollHelper.setScrollDirection(layoutManager.findFirstVisibleItemPosition() < p ? RecyclerAnimationScrollHelper.SCROLL_DIRECTION_DOWN : RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
            scrollHelper.scrollToPosition(p, offset, false, true);
        } else {
            LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(emojiGridView.getContext(), LinearSmoothScrollerCustom.POSITION_TOP) {
                @Override
                public void onEnd() {
                    smoothScrolling = false;
                }

                @Override
                protected void onStart() {
                    smoothScrolling = true;
                }
            };
            linearSmoothScroller.setTargetPosition(p);
            linearSmoothScroller.setOffset(offset);
            layoutManager.startSmoothScroll(linearSmoothScroller);
        }
    }
    public static class EmojiPackExpand extends FrameLayout {
        public TextView textView;

        public EmojiPackExpand(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            textView.setTextColor(0xffffffff);
            textView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(11), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_chat_emojiPanelStickerSetName, resourcesProvider), 99)));
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(1.66f), AndroidUtilities.dp(4), AndroidUtilities.dp(2f));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }
    }

    private View animateExpandFromButton;
    private float animateExpandFromButtonTranslate;
    private int animateExpandFromPosition = -1, animateExpandToPosition = -1;
    private long animateExpandStartTime = -1;

    public long animateExpandDuration() {
        return animateExpandAppearDuration() + animateExpandCrossfadeDuration() + 16;
    }

    public long animateExpandAppearDuration() {
        int count = animateExpandToPosition - animateExpandFromPosition;
        return Math.max(450, Math.min(55, count) * 30L);
    }

    public long animateExpandCrossfadeDuration() {
        int count = animateExpandToPosition - animateExpandFromPosition;
        return Math.max(300, Math.min(45, count) * 25L);
    }

    public class ImageViewEmoji extends View {
        public boolean empty = false;
        public boolean notDraw = false;
        public int position;
        public TLRPC.Document document;
        public AnimatedEmojiSpan span;
        public ImageReceiver.BackgroundThreadDrawHolder[] backgroundThreadDrawHolder = new ImageReceiver.BackgroundThreadDrawHolder[DrawingInBackgroundThreadDrawable.THREAD_COUNT];
        public ImageReceiver imageReceiver;
        public ImageReceiver preloadEffectImageReceiver = new ImageReceiver();
        public ImageReceiver imageReceiverToDraw;
        public boolean isDefaultReaction;
        public ReactionsLayoutInBubble.VisibleReaction reaction;
        public Drawable drawable;
        public boolean attached;
        ValueAnimator backAnimator;
        PremiumLockIconView premiumLockIconView;
        public boolean selected;
        private float pressedProgress;
        public float skewAlpha;
        public int skewIndex;
        public boolean isStaticIcon;
        private float selectedProgress;

        final AnimatedEmojiSpan.InvalidateHolder invalidateHolder = new AnimatedEmojiSpan.InvalidateHolder() {
            @Override
            public void invalidate() {
                if (getParent() != null) {
                    ((View)getParent()).invalidate();
                }
            }
        };

        public ImageViewEmoji(Context context) {
            super(context);
            preloadEffectImageReceiver.ignoreNotifications = true;
        }

        @Override
        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY));
        }

        @Override
        public void setPressed(boolean pressed) {
            if (isPressed() != pressed) {
                super.setPressed(pressed);
                invalidate();
                if (pressed) {
                    if (backAnimator != null) {
                        backAnimator.removeAllListeners();
                        backAnimator.cancel();
                    }
                }
                if (!pressed && pressedProgress != 0) {
                    backAnimator = ValueAnimator.ofFloat(pressedProgress, 0);
                    backAnimator.addUpdateListener(animation -> {
                        pressedProgress = (float) animation.getAnimatedValue();
                        emojiGridView.invalidate();
                    });
                    backAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            backAnimator = null;
                        }
                    });
                    backAnimator.setInterpolator(new OvershootInterpolator(5.0f));
                    backAnimator.setDuration(350);
                    backAnimator.start();
                }
            }
        }

        public void updatePressedProgress() {
            if (isPressed() && pressedProgress != 1f) {
                pressedProgress = Utilities.clamp(pressedProgress + 16f / 100f, 1f, 0);
                invalidate();
            }
        }

        public void update(long time) {
            if (imageReceiverToDraw != null) {
                if (imageReceiverToDraw.getLottieAnimation() != null) {
                    imageReceiverToDraw.getLottieAnimation().updateCurrentFrame(time, true);
                }
                if (imageReceiverToDraw.getAnimation() != null) {
                    imageReceiverToDraw.getAnimation().updateCurrentFrame(time, true);
                }
            }
        }

        public void drawSelected(Canvas canvas, View view) {
            if ((selected || selectedProgress > 0) && !notDraw) {
                if (selected && selectedProgress < 1f) {
                    selectedProgress += 16/ 300f;
                    view.invalidate();
                }
                if (!selected && selectedProgress > 0) {
                    selectedProgress -= 16/ 300f;
                    view.invalidate();
                }
                selectedProgress = Utilities.clamp(selectedProgress, 1f, 0f);

                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                AndroidUtilities.rectTmp.inset(AndroidUtilities.dp(1), AndroidUtilities.dp(1));
                Paint paint = empty || drawable instanceof AnimatedEmojiDrawable && ((AnimatedEmojiDrawable) drawable).canOverrideColor() ? selectorAccentPaint : selectorPaint;
                int wasAlpha = paint.getAlpha();
                paint.setAlpha((int) (wasAlpha * getAlpha() * selectedProgress));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                paint.setAlpha(wasAlpha);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (attached) {
                return;
            }
            attached = true;
            if (drawable instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) drawable).addView(invalidateHolder);
            }
            if (imageReceiver != null) {
                imageReceiver.setParentView((View) getParent());
                imageReceiver.onAttachedToWindow();
            }
            preloadEffectImageReceiver.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (!attached) {
                return;
            }
            attached = false;
            if (this.drawable instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) this.drawable).removeView(invalidateHolder);
            }
            if (imageReceiver != null) {
                imageReceiver.onDetachedFromWindow();
            }
            preloadEffectImageReceiver.onDetachedFromWindow();
        }

        public void setDrawable(Drawable drawable) {
            if (this.drawable != drawable) {
                if (attached && this.drawable != null && this.drawable instanceof AnimatedEmojiDrawable) {
                    ((AnimatedEmojiDrawable) this.drawable).removeView(invalidateHolder);
                }
                this.drawable = drawable;
                if (attached && drawable instanceof AnimatedEmojiDrawable) {
                    ((AnimatedEmojiDrawable) drawable).addView(invalidateHolder);
                }
            }

        }

        public void setSticker(TLRPC.Document document, View parent) {
            this.document = document;
            createImageReceiver(parent);
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
            imageReceiver.setImage(ImageLocation.getForDocument(document), "100_100_firstframe", null, null, svgThumb, 0, "tgs", document, 0);
            isStaticIcon = true;
            span = null;
        }

        public void createImageReceiver(View parent) {
            if (imageReceiver == null) {
                imageReceiver = new ImageReceiver(parent);
                imageReceiver.setLayerNum(7);
                if (attached) {
                    imageReceiver.onAttachedToWindow();
                }
                imageReceiver.setAspectFit(true);
            }
        }

        @Override
        public void invalidate() {
            if (getParent() != null) {
                ((View) getParent()).invalidate();
            }
        }
    }



    private void incrementHintUse() {
        if (type == TYPE_SET_DEFAULT_REACTION) {
            return;
        }
        final String key = "emoji" + (type==TYPE_EMOJI_STATUS ? "status" : "reaction") + "usehint";
        final int value = MessagesController.getGlobalMainSettings().getInt(key, 0);
        if (value <= 3) {
            MessagesController.getGlobalMainSettings().edit().putInt(key, value + 1).apply();
        }
    }

    public void expand(int position, View expandButton) {
        int index = positionToExpand.get(position);
        Integer from = null, count = null;
        boolean last;
        int maxlen;
        int fromCount, start, toCount;
        animateExpandFromButtonTranslate = 0;
        if (index >= 0 && index < packs.size()) {
            int EXPAND_MAX_LINES = 3;
            maxlen = SPAN_COUNT_FOR_EMOJI * EXPAND_MAX_LINES;
            EmojiView.EmojiPack pack = packs.get(index);
            if (pack.expanded) {
                return;
            }
            last = index + 1 == packs.size();

            start = sectionToPosition.get(index);
            expandedEmojiSets.add(pack.set.id);

            fromCount = pack.expanded ? pack.documents.size() : Math.min(maxlen, pack.documents.size());
            if (pack.documents.size() > maxlen) {
                from = start + 1 + fromCount;
            }
            pack.expanded = true;
            toCount = pack.documents.size();
        } else {
            return;
        }
        if (toCount > fromCount) {
            from = start + 1 + fromCount;
            count = toCount - fromCount;
        }

        if (from != null && count != null) {
            animateExpandFromButton = expandButton;
            animateExpandFromPosition = from;
            animateExpandToPosition = from + count;
            animateExpandStartTime = SystemClock.elapsedRealtime();

            if (last) {
                final int scrollTo = from;
                final float durationMultiplier = count > maxlen / 2 ? 1.5f : 3.5f;
                post(() -> {
                    try {
                        LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(emojiGridView.getContext(), LinearSmoothScrollerCustom.POSITION_MIDDLE, durationMultiplier);
                        linearSmoothScroller.setTargetPosition(scrollTo);
                        layoutManager.startSmoothScroll(linearSmoothScroller);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
            }
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

        SparseArray<ArrayList<ImageViewEmoji>> viewsGroupedByLines = new SparseArray<>();
        ArrayList<ArrayList<ImageViewEmoji>> unusedArrays = new ArrayList<>();
        private boolean invalidated;

        private LongSparseArray<AnimatedEmojiDrawable> animatedEmojiDrawables = new LongSparseArray<>();

        @Override
        public boolean drawChild(Canvas canvas, View child, long drawingTime) {
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected boolean canHighlightChildAt(View child, float x, float y) {
            if (child instanceof ImageViewEmoji && (((ImageViewEmoji) child).empty || ((ImageViewEmoji) child).drawable instanceof AnimatedEmojiDrawable && ((AnimatedEmojiDrawable) ((ImageViewEmoji) child).drawable).canOverrideColor())) {
                setSelectorDrawableColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider), 30));
            } else {
                setSelectorDrawableColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));
            }
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

            for (int i = 0; i < viewsGroupedByLines.size(); i++) {
                ArrayList<ImageViewEmoji> arrayList = viewsGroupedByLines.valueAt(i);
                arrayList.clear();
                unusedArrays.add(arrayList);
            }
            viewsGroupedByLines.clear();
            final boolean animatedExpandIn = animateExpandStartTime > 0 && (SystemClock.elapsedRealtime() - animateExpandStartTime) < animateExpandDuration();
            final boolean drawButton = animatedExpandIn && animateExpandFromButton != null && animateExpandFromPosition >= 0;
            if (animatedEmojiDrawables != null) {
                for (int i = 0; i < getChildCount(); ++i) {
                    View child = getChildAt(i);
                    if (child instanceof ImageViewEmoji) {
                        ImageViewEmoji imageViewEmoji = (ImageViewEmoji) child;
                        imageViewEmoji.updatePressedProgress();
                        int top = smoothScrolling ? (int) child.getY() : child.getTop();
                        ArrayList<ImageViewEmoji> arrayList = viewsGroupedByLines.get(top);

                        canvas.save();
                        canvas.translate(imageViewEmoji.getX(), imageViewEmoji.getY());
                        imageViewEmoji.drawSelected(canvas, this);
                        canvas.restore();

                        if (imageViewEmoji.getBackground() != null) {
                            imageViewEmoji.getBackground().setBounds((int) imageViewEmoji.getX(), (int) imageViewEmoji.getY(), (int) imageViewEmoji.getX() + imageViewEmoji.getWidth(), (int) imageViewEmoji.getY() + imageViewEmoji.getHeight());
                            int wasAlpha = 255;
                            imageViewEmoji.getBackground().setAlpha((int) (wasAlpha * imageViewEmoji.getAlpha()));
                            imageViewEmoji.getBackground().draw(canvas);
                            imageViewEmoji.getBackground().setAlpha(wasAlpha);
                        }

                        if (arrayList == null) {
                            if (!unusedArrays.isEmpty()) {
                                arrayList = unusedArrays.remove(unusedArrays.size() - 1);
                            } else {
                                arrayList = new ArrayList<>();
                            }
                            viewsGroupedByLines.put(top, arrayList);
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
            for (int i = 0; i < viewsGroupedByLines.size(); i++) {
                ArrayList<ImageViewEmoji> arrayList = viewsGroupedByLines.valueAt(i);
                ImageViewEmoji firstView = arrayList.get(0);
                canvas.save();
                canvas.translate(firstView.getLeft(), firstView.getY()/* + firstView.getPaddingTop()*/);
                canvas.restore();
            }
            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (child instanceof ImageViewEmoji) {
                    ImageViewEmoji imageViewEmoji = (ImageViewEmoji) child;
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

//    private final Runnable updateRowsDelayed = () -> NotificationCenter.getInstance(currentAccount).doOnIdle(() -> updateRows(true, true));

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.featuredEmojiDidLoad) {
            NotificationCenter.getGlobalInstance().doOnIdle(() -> {
                AndroidUtilities.runOnUIThread(() -> {
//                    updateRows(false, true);
                }, 120);
            });
        } else if (id == NotificationCenter.recentEmojiStatusesUpdate) {
            NotificationCenter.getGlobalInstance().doOnIdle(() -> {
                AndroidUtilities.runOnUIThread(() -> {
//                    updateRows(false, true);
                }, 120);
            });
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
            for (int i = 0; i < emojiGridView.getChildCount(); ++i) {
                View child = emojiGridView.getChildAt(i);
                child.setScaleX(1);
                child.setScaleY(1);
            }
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
                    for (int i = 0; i < emojiGridView.getChildCount(); ++i) {
                        View child = emojiGridView.getChildAt(i);
                        child.setScaleX(1);
                        child.setScaleY(1);
                    }
                    for (int i = 0; i < emojiTabs.contentView.getChildCount(); ++i) {
                        View child = emojiTabs.contentView.getChildAt(i);
                        child.setScaleX(1);
                        child.setScaleY(1);
                    }
                    emojiTabs.contentView.invalidate();
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
            bubble2View.setAlpha(bubble2t);
            bubble2View.setScaleX(bubble2t);
            bubble2View.setScaleY(bubble2t);
        }

        float containerx = MathUtils.clamp((t * showDuration - 40) / 700, 0, 1);
        float containery = MathUtils.clamp((t * showDuration - 80) / 700, 0, 1);
        float containeritemst = MathUtils.clamp((t * showDuration - 40) / 750, 0, 1);
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
        emojiTabsShadow.setAlpha(containeralphat);
        emojiTabsShadow.setScaleX(Math.min(scaleX, 1));

        final float px = emojiTabsShadow.getPivotX();
        final float fullr = (float) Math.sqrt(Math.max(
            px * px + Math.pow(contentView.getHeight(), 2),
            Math.pow(contentView.getWidth() - px, 2) + Math.pow(contentView.getHeight(), 2)
        ));
        for (int i = 0; i < emojiTabs.contentView.getChildCount(); ++i) {
            View child = emojiTabs.contentView.getChildAt(i);
            float ccx = child.getLeft() + child.getWidth() / 2f, ccy = child.getTop() + child.getHeight() / 2f;
            float distance = (float) Math.sqrt((ccx - px) * (ccx - px) + ccy * ccy * .4f);
            float scale = AndroidUtilities.cascade(containeritemst, distance, fullr, child.getHeight() * 1.75f);
            if (Float.isNaN(scale)) {
                scale = 0;
            }
            child.setScaleX(scale);
            child.setScaleY(scale);
        }
        emojiTabs.contentView.invalidate();
        for (int i = 0; i < emojiGridView.getChildCount(); ++i) {
            View child = emojiGridView.getChildAt(i);
            float cx = child.getLeft() + child.getWidth() / 2f, cy = child.getTop() + child.getHeight() / 2f;
            float distance = (float) Math.sqrt((cx - px) * (cx - px) + cy * cy * .2f);
            float scale = AndroidUtilities.cascade(containeritemst, distance, fullr, child.getHeight() * 1.75f);
            if (Float.isNaN(scale))
                scale = 0;
            child.setScaleX(scale);
            child.setScaleY(scale);
        }
        emojiGridView.invalidate();
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
                if (selectStatusDateDialog != null) {
                    selectStatusDateDialog.dismiss();
                    selectStatusDateDialog = null;
                }
            }
        });
        hideAnimator.setDuration(200);
        hideAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        hideAnimator.start();
    }

    public void setDrawBackground(boolean drawBackground) {
        this.drawBackground = drawBackground;
    }

    private class SelectStatusDurationDialog extends Dialog {
        private ImageViewEmoji imageViewEmoji;
        private ImageReceiver imageReceiver;
        private Rect from = new Rect(), to = new Rect(), current = new Rect();
        private Runnable parentDialogDismiss;
        private View parentDialogView;
        private Bitmap blurBitmap;
        private Paint blurBitmapPaint;

        private WindowInsets lastInsets;
        private ContentView contentView;

        private LinearLayout linearLayoutView;
        private View emojiPreviewView;
        private ActionBarPopupWindow.ActionBarPopupWindowLayout menuView;

        private BottomSheet dateBottomSheet;
        private boolean changeToScrimColor;

        private int parentDialogX, parentDialogY;
        private int clipBottom;

        private int[] tempLocation = new int[2];

        private class ContentView extends FrameLayout {
            public ContentView(Context context) {
                super(context);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
                );
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (blurBitmap != null && blurBitmapPaint != null) {
                    canvas.save();
                    canvas.scale(12f, 12f);
                    blurBitmapPaint.setAlpha((int) (255 * showT));
                    canvas.drawBitmap(blurBitmap, 0, 0, blurBitmapPaint);
                    canvas.restore();
                }
                super.dispatchDraw(canvas);
                if (imageViewEmoji != null) {
                    Drawable drawable = imageViewEmoji.drawable;
                    if (drawable != null) {
                            drawable.setColorFilter(premiumStarColorFilter);
                        drawable.setAlpha((int) (255 * (1f - showT)));
                        AndroidUtilities.rectTmp.set(current);
                        float scale = 1f;
                        if (imageViewEmoji.pressedProgress != 0 || imageViewEmoji.selected) {
                            scale *= 0.8f + 0.2f * (1f - (imageViewEmoji.selected ? .7f : imageViewEmoji.pressedProgress));
                        }
                        AndroidUtilities.rectTmp2.set(
                            (int) (AndroidUtilities.rectTmp.centerX() - AndroidUtilities.rectTmp.width() / 2 * scale),
                            (int) (AndroidUtilities.rectTmp.centerY() - AndroidUtilities.rectTmp.height() / 2 * scale),
                            (int) (AndroidUtilities.rectTmp.centerX() + AndroidUtilities.rectTmp.width() / 2 * scale),
                            (int) (AndroidUtilities.rectTmp.centerY() + AndroidUtilities.rectTmp.height() / 2 * scale)
                        );
                        float skew = 1f - (1f - imageViewEmoji.skewAlpha) * (1f - showT);
                        canvas.save();
                        if (skew < 1) {
                            canvas.translate(AndroidUtilities.rectTmp2.left, AndroidUtilities.rectTmp2.top);
                            canvas.scale(1f, skew, 0, 0);
                            canvas.skew((1f - 2f * imageViewEmoji.skewIndex / SPAN_COUNT_FOR_EMOJI) * (1f - skew), 0);
                            canvas.translate(-AndroidUtilities.rectTmp2.left, -AndroidUtilities.rectTmp2.top);
                        }
                        canvas.clipRect(0, 0, getWidth(), clipBottom + showT * AndroidUtilities.dp(45));
                        drawable.setBounds(AndroidUtilities.rectTmp2);
                        drawable.draw(canvas);
                        canvas.restore();

                        if (imageViewEmoji.skewIndex == 0) {
                            AndroidUtilities.rectTmp2.offset(AndroidUtilities.dp(8 * skew), 0);
                        } else if (imageViewEmoji.skewIndex == 1) {
                            AndroidUtilities.rectTmp2.offset(AndroidUtilities.dp(4 * skew), 0);
                        } else if (imageViewEmoji.skewIndex == SPAN_COUNT_FOR_EMOJI - 2) {
                            AndroidUtilities.rectTmp2.offset(-AndroidUtilities.dp(-4 * skew), 0);
                        } else if (imageViewEmoji.skewIndex == SPAN_COUNT_FOR_EMOJI - 1) {
                            AndroidUtilities.rectTmp2.offset(AndroidUtilities.dp(-8 * skew), 0);
                        }
                        canvas.saveLayerAlpha(AndroidUtilities.rectTmp2.left, AndroidUtilities.rectTmp2.top, AndroidUtilities.rectTmp2.right, AndroidUtilities.rectTmp2.bottom, (int) (255 * (1f - showT)), Canvas.ALL_SAVE_FLAG);
                        canvas.clipRect(AndroidUtilities.rectTmp2);
                        canvas.translate((int) (bottomGradientView.getX() + SelectAnimatedBlogTypeDialog.this.contentView.getX() + parentDialogX), (int) bottomGradientView.getY() + SelectAnimatedBlogTypeDialog.this.contentView.getY() + parentDialogY);
                        bottomGradientView.draw(canvas);
                        canvas.restore();

                    } else if (imageViewEmoji.isDefaultReaction && imageViewEmoji.imageReceiver != null) {
                        imageViewEmoji.imageReceiver.setAlpha(1f - showT);
                        imageViewEmoji.imageReceiver.setImageCoords(current);
                        imageViewEmoji.imageReceiver.draw(canvas);
                    }
                }
                if (imageReceiver != null) {
                    imageReceiver.setAlpha(showT);
                    imageReceiver.setImageCoords(current);
                    imageReceiver.draw(canvas);
                }
            }

            @Override
            protected void onConfigurationChanged(Configuration newConfig) {
                lastInsets = null;
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                if (imageReceiver != null) {
                    imageReceiver.onAttachedToWindow();
                }
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                if (imageReceiver != null) {
                    imageReceiver.onDetachedFromWindow();
                }
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                Activity parentActivity = getParentActivity();
                if (parentActivity == null) {
                    return;
                }
                View parentView = parentActivity.getWindow().getDecorView();
                if (blurBitmap == null || blurBitmap.getWidth() != parentView.getMeasuredWidth() || blurBitmap.getHeight() != parentView.getMeasuredHeight()) {
                    prepareBlurBitmap();
                }
            }
        }

        public SelectStatusDurationDialog(Context context, Runnable parentDialogDismiss, View parentDialogView, ImageViewEmoji imageViewEmoji, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.imageViewEmoji = imageViewEmoji;
            resourcesProvider = resourcesProvider;
            this.parentDialogDismiss = parentDialogDismiss;
            this.parentDialogView = parentDialogView;

            setContentView(
                this.contentView = new ContentView(context),
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            );

            linearLayoutView = new LinearLayout(context);
            linearLayoutView.setOrientation(LinearLayout.VERTICAL);

            emojiPreviewView = new View(context) {
                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    super.onLayout(changed, left, top, right, bottom);
                    getLocationOnScreen(tempLocation);
                    to.set(
                        tempLocation[0],
                        tempLocation[1],
                        tempLocation[0] + getWidth(),
                        tempLocation[1] + getHeight()
                    );
                    AndroidUtilities.lerp(from, to, showT, current);
                }
            };
            linearLayoutView.addView(emojiPreviewView, LayoutHelper.createLinear(160, 160, Gravity.CENTER, 0, 0, 0, 16));

            menuView = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, R.drawable.popup_fixed_alert2, resourcesProvider);
            linearLayoutView.addView(menuView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 0));


            ActionBarMenuItem.addItem(true, false, menuView, 0, LocaleController.getString("SetEmojiStatusUntil1Hour", R.string.SetEmojiStatusUntil1Hour), false, resourcesProvider)
                .setOnClickListener(e -> done((int) (System.currentTimeMillis() / 1000 + 60 * 60)));
            ActionBarMenuItem.addItem(false, false, menuView, 0, LocaleController.getString("SetEmojiStatusUntil2Hours", R.string.SetEmojiStatusUntil2Hours), false, resourcesProvider)
                .setOnClickListener(e -> done((int) (System.currentTimeMillis() / 1000 + 2 * 60 * 60)));
            ActionBarMenuItem.addItem(false, false, menuView, 0, LocaleController.getString("SetEmojiStatusUntil8Hours", R.string.SetEmojiStatusUntil8Hours), false, resourcesProvider)
                .setOnClickListener(e -> done((int) (System.currentTimeMillis() / 1000 + 8 * 60 * 60)));
            ActionBarMenuItem.addItem(false, false, menuView, 0, LocaleController.getString("SetEmojiStatusUntil2Days", R.string.SetEmojiStatusUntil2Days), false, resourcesProvider)
                .setOnClickListener(e -> done((int) (System.currentTimeMillis() / 1000 + 2 * 24 * 60 * 60)));
            ActionBarMenuItem.addItem(false, true, menuView, 0, LocaleController.getString("SetEmojiStatusUntilOther", R.string.SetEmojiStatusUntilOther), false, resourcesProvider)
                .setOnClickListener(e -> {
                    if (dateBottomSheet != null) {
                        return;
                    }
                    boolean[] selected = new boolean[1];
                    BottomSheet.Builder builder = AlertsCreator.createStatusUntilDatePickerDialog(context, System.currentTimeMillis() / 1000, date -> {
                        selected[0] = true;
                        done(date);
                    });
                    builder.setOnPreDismissListener(di -> {
                        if (!selected[0]) {
                            animateMenuShow(true, null);
                        }
                        dateBottomSheet = null;
                    });
                    dateBottomSheet = builder.show();
                    animateMenuShow(false, null);
                });

            contentView.addView(linearLayoutView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            Window window = getWindow();
            if (window != null) {
                window.setWindowAnimations(R.style.DialogNoAnimation);
                window.setBackgroundDrawable(null);

                WindowManager.LayoutParams params = window.getAttributes();
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.gravity = Gravity.TOP | Gravity.LEFT;
                params.dimAmount = 0;
                params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
                if (Build.VERSION.SDK_INT >= 21) {
                    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
                    contentView.setOnApplyWindowInsetsListener((v, insets) -> {
                        lastInsets = insets;
                        v.requestLayout();
                        return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
                    });
                }
                params.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
                contentView.setFitsSystemWindows(true);
                contentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                if (Build.VERSION.SDK_INT >= 28) {
                    params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                }
                window.setAttributes(params);
            }

            if (imageViewEmoji != null) {
                imageViewEmoji.notDraw = true;
            }
            prepareBlurBitmap();

            imageReceiver = new ImageReceiver();
            imageReceiver.setParentView(contentView);
            imageReceiver.setLayerNum(7);
            TLRPC.Document document = imageViewEmoji.document;
            if (document == null && imageViewEmoji != null && imageViewEmoji.drawable instanceof AnimatedEmojiDrawable) {
                document = ((AnimatedEmojiDrawable) imageViewEmoji.drawable).getDocument();
            }
            if (document != null) {
                String filter = "160_160";
                ImageLocation mediaLocation;
                String mediaFilter;
                SvgHelper.SvgDrawable thumbDrawable = DocumentObject.getSvgThumb(document.thumbs, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                if ("video/webm".equals(document.mime_type)) {
                    mediaLocation = ImageLocation.getForDocument(document);
                    mediaFilter = filter + "_" + ImageLoader.AUTOPLAY_FILTER;
                    if (thumbDrawable != null) {
                        thumbDrawable.overrideWidthAndHeight(512, 512);
                    }
                } else {
                    if (thumbDrawable != null && MessageObject.isAnimatedStickerDocument(document, false)) {
                        thumbDrawable.overrideWidthAndHeight(512, 512);
                    }
                    mediaLocation = ImageLocation.getForDocument(document);
                    mediaFilter = filter;
                }
                imageReceiver.setImage(mediaLocation, mediaFilter, ImageLocation.getForDocument(thumb, document), filter, null, null, thumbDrawable, document.size, null, document, 1);
                if (imageViewEmoji.drawable instanceof AnimatedEmojiDrawable && ((AnimatedEmojiDrawable) imageViewEmoji.drawable).canOverrideColor()) {
                    imageReceiver.setColorFilter(AnimatedEmojiDrawable.isDefaultStatusEmoji((AnimatedEmojiDrawable) imageViewEmoji.drawable) ? premiumStarColorFilter : Theme.chat_animatedEmojiTextColorFilter);
                }
            }

            imageViewEmoji.getLocationOnScreen(tempLocation);
            from.left = tempLocation[0] + imageViewEmoji.getPaddingLeft();
            from.top = tempLocation[1] + imageViewEmoji.getPaddingTop();
            from.right = tempLocation[0] + imageViewEmoji.getWidth() - imageViewEmoji.getPaddingRight();
            from.bottom = tempLocation[1] + imageViewEmoji.getHeight() - imageViewEmoji.getPaddingBottom();
            AndroidUtilities.lerp(from, to, showT, current);

            parentDialogView.getLocationOnScreen(tempLocation);
            parentDialogX = tempLocation[0];
            clipBottom = (parentDialogY = tempLocation[1]) + parentDialogView.getHeight();
        }

        private boolean done = false;
        private void done(Integer date) {
            if (done) {
                return;
            }
            done = true;
            boolean showback;
            if (showback = changeToScrimColor = date != null ) {
                parentDialogView.getLocationOnScreen(tempLocation);
                from.offset(tempLocation[0], tempLocation[1]);
            } else {
                imageViewEmoji.getLocationOnScreen(tempLocation);
                from.left = tempLocation[0] + imageViewEmoji.getPaddingLeft();
                from.top = tempLocation[1] + imageViewEmoji.getPaddingTop();
                from.right = tempLocation[0] + imageViewEmoji.getWidth() - imageViewEmoji.getPaddingRight();
                from.bottom = tempLocation[1] + imageViewEmoji.getHeight() - imageViewEmoji.getPaddingBottom();
            }
            if (date != null && parentDialogDismiss != null) {
                parentDialogDismiss.run();
            }
            animateShow(false, () -> {
                try {
                    super.dismiss();
                } catch (Exception ignore) {

                }
            }, () -> {
                if (date != null) {
                    try {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    } catch (Exception ignore) {}
                }
            }, !showback);
            animateMenuShow(false, null);
        }

        private Activity getParentActivity() {
            Context currentContext = getContext();
            while (currentContext instanceof ContextWrapper) {
                if (currentContext instanceof Activity)
                    return (Activity) currentContext;
                currentContext = ((ContextWrapper) currentContext).getBaseContext();
            }
            return null;
        }

        private void prepareBlurBitmap() {
            Activity parentActivity = getParentActivity();
            if (parentActivity == null) {
                return;
            }
            View parentView = parentActivity.getWindow().getDecorView();
            int w = (int) (parentView.getMeasuredWidth() / 12.0f);
            int h = (int) (parentView.getMeasuredHeight() / 12.0f);
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(1.0f / 12.0f, 1.0f / 12.0f);
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            parentView.draw(canvas);
            if (parentActivity instanceof LaunchActivity && ((LaunchActivity) parentActivity).getActionBarLayout().getLastFragment().getVisibleDialog() != null) {
                ((LaunchActivity) parentActivity).getActionBarLayout().getLastFragment().getVisibleDialog().getWindow().getDecorView().draw(canvas);
            }
            if (parentDialogView != null) {
                parentDialogView.getLocationOnScreen(tempLocation);
                canvas.save();
                canvas.translate(tempLocation[0], tempLocation[1]);
                parentDialogView.draw(canvas);
                canvas.restore();
            }
            Utilities.stackBlurBitmap(bitmap, Math.max(10, Math.max(w, h) / 180));
            blurBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            blurBitmap = bitmap;
        }

        private float showT;
        private boolean showing;
        private ValueAnimator showAnimator;
        private void animateShow(boolean show, Runnable onDone, Runnable onPartly, boolean showback) {
            if (imageViewEmoji == null) {
                if (onDone != null) {
                    onDone.run();
                }
                return;
            }
            if (showAnimator != null) {
                if (showing == show) {
                    return;
                }
                showAnimator.cancel();
            }
            showing = show;
            if (show) {
                imageViewEmoji.notDraw = true;
            }
            final boolean[] partlydone = new boolean[1];
            showAnimator = ValueAnimator.ofFloat(showT, show ? 1f : 0);
            showAnimator.addUpdateListener(anm -> {
                showT = (float) anm.getAnimatedValue();
                AndroidUtilities.lerp(from, to, showT, current);
                contentView.invalidate();

                if (!show) {
                    menuView.setAlpha(showT);
                }

                if (showT < 0.025f && !show) {
                    if (showback) {
                        imageViewEmoji.notDraw = false;
                        emojiGridView.invalidate();
                    }
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 4);
                }

                if (showT < .5f && !show && onPartly != null && !partlydone[0]) {
                    partlydone[0] = true;
                    onPartly.run();
                }
            });
            showAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    showT = show ? 1f : 0;
                    AndroidUtilities.lerp(from, to, showT, current);
                    contentView.invalidate();
                    if (!show) {
                        menuView.setAlpha(showT);
                    }
                    if (showT < .5f && !show && onPartly != null && !partlydone[0]) {
                        partlydone[0] = true;
                        onPartly.run();
                    }

                    if (!show) {
                        if (showback) {
                            imageViewEmoji.notDraw = false;
                            emojiGridView.invalidate();
                        }
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 4);
                    }
                    showAnimator = null;
                    contentView.invalidate();
                    if (onDone != null) {
                        onDone.run();
                    }
                }
            });
            showAnimator.setDuration(420);
            showAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            showAnimator.start();
        }

        private float showMenuT;
        private boolean showingMenu;
        private ValueAnimator showMenuAnimator;
        private void animateMenuShow(boolean show, Runnable onDone) {
            if (showMenuAnimator != null) {
                if (showingMenu == show) {
                    return;
                }
                showMenuAnimator.cancel();
            }
            showingMenu = show;
            showMenuAnimator = ValueAnimator.ofFloat(showMenuT, show ? 1f : 0);
            showMenuAnimator.addUpdateListener(anm -> {
                showMenuT = (float) anm.getAnimatedValue();

                menuView.setBackScaleY(showMenuT);
                menuView.setAlpha(CubicBezierInterpolator.EASE_OUT.getInterpolation(showMenuT));
                final int count = menuView.getItemsCount();
                for (int i = 0; i < count; ++i) {
                    final float at = AndroidUtilities.cascade(showMenuT, i, count, 4);
                    menuView.getItemAt(i).setTranslationY((1f - at) * AndroidUtilities.dp(-12));
                    menuView.getItemAt(i).setAlpha(at);
                }
            });
            showMenuAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    showMenuT = show ? 1f : 0;

                    menuView.setBackScaleY(showMenuT);
                    menuView.setAlpha(CubicBezierInterpolator.EASE_OUT.getInterpolation(showMenuT));
                    final int count = menuView.getItemsCount();
                    for (int i = 0; i < count; ++i) {
                        final float at = AndroidUtilities.cascade(showMenuT, i, count, 4);
                        menuView.getItemAt(i).setTranslationY((1f - at) * AndroidUtilities.dp(-12));
                        menuView.getItemAt(i).setAlpha(at);
                    }

                    showMenuAnimator = null;
                    if (onDone != null) {
                        onDone.run();
                    }
                }
            });
            if (show) {
                showMenuAnimator.setDuration(360);
                showMenuAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            } else {
                showMenuAnimator.setDuration(240);
                showMenuAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            }
            showMenuAnimator.start();
        }

        @Override
        public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
            boolean res = super.dispatchTouchEvent(ev);
            if (!res && ev.getAction() == MotionEvent.ACTION_DOWN) {
                dismiss();
                return false;
            }
            return res;
        }

        @Override
        public void show() {
            super.show();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 4);
            animateShow(true, null, null,true);
            animateMenuShow(true, null);
        }

        private boolean dismissed = false;
        @Override
        public void dismiss() {
            if (dismissed) {
                return;
            }
            done(null);
            dismissed = true;
        }
    }

    @Override
    public void setPressed(boolean pressed) {
       return;
    }
}
