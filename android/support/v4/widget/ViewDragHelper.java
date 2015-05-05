/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package android.support.v4.widget;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import java.util.Arrays;

/**
 * @tranlator Alex Tam
 * ViewDragHelper是自定义ViewGroup时的实用类.它提供大量有用的操作和状态,来追踪用户在父View内的
 * 拖曳子view和重新定位子view.(看到这里,估计就会想,要同步监听拖曳事件,肯定少不了在onTouch事件中随处用到的
 * MotionEvent这个对象.是的,下面的确有它.)
 * 
 * ViewDragHelper is a utility class for writing custom ViewGroups. It offers a number
 * of useful operations and state tracking for allowing a user to drag and reposition
 * views within their parent ViewGroup.
 */
public class ViewDragHelper {
    private static final String TAG = "ViewDragHelper";

    /**
     * 空/无效的pointer ID
     * A null/invalid pointer ID.
     */
    public static final int INVALID_POINTER = -1;

    /**
     * 状态量:(IDLE是闲置的 意思)表示 view当前没有被拖曳或者运行的动画结束
     * A view is not currently being dragged or animating as a result of a fling/snap.
     */
    public static final int STATE_IDLE = 0;

    /**
     * 状态量: view当前正被拖曳.根据用户的输入或者模拟用户的输入,view当前位置发生改变.(表示,
     * 用户怎么拖曳移动,view就根据拖曳的动作而发生位置改变.)
     * A view is currently being dragged. The position is currently changing as a result
     * of user input or simulated user input.
     */
    public static final int STATE_DRAGGING = 1;

    /**
     * 状态量: 由于fling动作,或者预定的无交互的运动,view被"安置"到一个结束的地方.(可以想象,一个view被用户快速拖曳
     * 并甩动,从而view被甩到某个结束的位置,的过程.)
     * A view is currently settling into place as a result of a fling or
     * predefined non-interactive motion.
     */
    public static final int STATE_SETTLING = 2;

    /**
     * 标记可从左边缘拖曳.
     * Edge flag indicating that the left edge should be affected.
     */
    public static final int EDGE_LEFT = 1 << 0;

    /**
     * 标记可从右边缘拖曳.
     * Edge flag indicating that the right edge should be affected.
     */
    public static final int EDGE_RIGHT = 1 << 1;

    /**
     * 标记可从顶部拖曳.
     * Edge flag indicating that the top edge should be affected.
     */
    public static final int EDGE_TOP = 1 << 2;

    /**
     * 标记可从底部拖曳.
     * Edge flag indicating that the bottom edge should be affected.
     */
    public static final int EDGE_BOTTOM = 1 << 3;

    /**
     * 标记所有地方(边缘的上下左右)都能被拖曳.
     * Edge flag set indicating all edges should be affected.
     */
    public static final int EDGE_ALL = EDGE_LEFT | EDGE_TOP | EDGE_RIGHT | EDGE_BOTTOM;

    /**
     * 指引值:
     * 
     * 表示a check(指引) 应该沿着水平轴发生.
     * Indicates that a check should occur along the horizontal axis
     */
    public static final int DIRECTION_HORIZONTAL = 1 << 0;

    /**
     * 表示a check 应该沿着垂直轴发生.
     * Indicates that a check should occur along the vertical axis
     */
    public static final int DIRECTION_VERTICAL = 1 << 1;

    /**
     * 表示a check可水平可垂直的发生.
     * Indicates that a check should occur along all axes
     */
    public static final int DIRECTION_ALL = DIRECTION_HORIZONTAL | DIRECTION_VERTICAL;

    //将边缘大小定位20dp
    private static final int EDGE_SIZE = 20; // dp

    //时间值
    private static final int BASE_SETTLE_DURATION = 256; // ms
    private static final int MAX_SETTLE_DURATION = 600; // ms

    // 当前的拖曳状态,值为idle, dragging or settling.
    // Current drag state; idle, dragging or settling
    private int mDragState;

    // 在拖曳开始前的滑动位移.(可以这么理解,触发拖曳的最大临界值.)
    // Distance to travel before a drag may begin
    private int mTouchSlop;
    
    // 上一次的位置或点
    // Last known position/pointer tracking
    private int mActivePointerId = INVALID_POINTER;
    //初始化的X坐标
    private float[] mInitialMotionX;
    //初始化的Y坐标
    private float[] mInitialMotionY;
    //下面这些变量不写了噻,看名字也能知道.
    private float[] mLastMotionX;
    private float[] mLastMotionY;
    private int[] mInitialEdgesTouched;
    private int[] mEdgeDragsInProgress;
    private int[] mEdgeDragsLocked;
    private int mPointersDown;

    private VelocityTracker mVelocityTracker;
    private float mMaxVelocity;
    private float mMinVelocity;
    //边缘的大小,单位px
    private int mEdgeSize;
    private int mTrackingEdges;

    //兼容新API所提供的Scroller
    private ScrollerCompat mScroller;
    
    //内部抽象类,提供一些规范的接口方法
    private final Callback mCallback;

    private View mCapturedView;
    private boolean mReleaseInProgress;

    private final ViewGroup mParentView;

    /**
     * 这个Callback是作为通信接口,当ViewDragHelper返回父view时使用."on"为首的方法是重要事件的回调方法,几个
     * 接口方法用于提供更多关于请求父view的状态的信息给ViewDragHelper.这个抽象类同时提供子view拖曳的一些细节信息.
     * 
     * A Callback is used as a communication channel with the ViewDragHelper back to the
     * parent view using it. <code>on*</code>methods are invoked on siginficant events and several
     * accessor methods are expected to provide the ViewDragHelper with more information
     * about the state of the parent view upon request. The callback also makes decisions
     * governing the range and draggability of child views.
     */
    public static abstract class Callback {
        /**
         * 当拖曳状态变更时回调该方法.可看"STATE_"为首的常量了解更多信息.
         * Called when the drag state changes. See the <code>STATE_*</code> constants
         * for more information.
         *
         * @param state The new drag state
         *
         * @see #STATE_IDLE
         * @see #STATE_DRAGGING
         * @see #STATE_SETTLING
         */
        public void onViewDragStateChanged(int state) {}

        /**
         * 当捕获view由于拖曳或者设定而发生位置变更时回调..
         * Called when the captured view's position changes as the result of a drag or settle.
         *
         * @param changedView View whose position changed - 发生位置变更的view
         * @param left New X coordinate of the left edge of the view - 新的左边缘X坐标
         * @param top New Y coordinate of the top edge of the view	- 新的顶部边缘Y坐标
         * @param dx Change in X position from the last call	- 从旧到新位置发生的X偏移值
         * @param dy Change in Y position from the last call	- 从旧到新位置发生的Y偏移值
         */
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {}

        /**
         * 当子view被由于拖曳或设置(settle有点难翻译)而被捕获时回调的方法.提供拖曳的pointer的ID.
         * 如果activePointerId被标记为{@link #INVALID_POINTER},它会代替没有初始化的pointer.
         * 
         * Called when a child view is captured for dragging or settling. The ID of the pointer
         * currently dragging the captured view is supplied. If activePointerId is
         * identified as {@link #INVALID_POINTER} the capture is programmatic instead of
         * pointer-initiated.
         *
         * @param capturedChild Child view that was captured
         * @param activePointerId Pointer id tracking the child capture
         */
        public void onViewCaptured(View capturedChild, int activePointerId) {}

        /**
         * 当子view不再被拖曳时调用.如果有需要,fling的速度也会被提供.速度值会介于系统最小化和最大值之间.
         * 
         * Called when the child view is no longer being actively dragged.
         * The fling velocity is also supplied, if relevant. The velocity values may
         * be clamped to system minimums or maximums.
         *
         * <p>Calling code may decide to fling or otherwise release the view to let it
         * settle into place. It should do so using {@link #settleCapturedViewAt(int, int)}
         * or {@link #flingCapturedView(int, int, int, int)}. If the Callback invokes
         * one of these methods, the ViewDragHelper will enter {@link #STATE_SETTLING}
         * and the view capture will not fully end until it comes to a complete stop.
         * If neither of these methods is invoked before <code>onViewReleased</code> returns,
         * the view will stop in place and the ViewDragHelper will return to
         * {@link #STATE_IDLE}.</p>
         *
         * @param releasedChild The captured child view now being released
         * 		- 被捕获到的要释放的子view
         * @param xvel X velocity of the pointer as it left the screen in pixels per second.
         * 		- pointer离开屏幕X轴方向每秒运动的速率,单位是px.
         * @param yvel Y velocity of the pointer as it left the screen in pixels per second.
         * 		- pointer离开屏幕Y轴方向每秒运动的速率,单位是px.
         */
        public void onViewReleased(View releasedChild, float xvel, float yvel) {}

        /**
         * 当父view其中一个被标记可拖曳的边缘被用户触摸, 同时父view里没有子view被捕获响应时回调该方法.
         * Called when one of the subscribed edges in the parent view has been touched
         * by the user while no child view is currently captured.
         *
         * @param edgeFlags A combination of edge flags describing the edge(s) currently touched
         * 			- 描述所当前所触摸的位置的边缘标记, 如EDGE_LEFT,EDGE_RIGHT等等.
         * @param pointerId ID of the pointer touching the described edge(s)
         * 			- 触摸的点的ID.
         * 
         * @see #EDGE_LEFT
         * @see #EDGE_TOP
         * @see #EDGE_RIGHT
         * @see #EDGE_BOTTOM
         */
        public void onEdgeTouched(int edgeFlags, int pointerId) {}

        /**
         * 该方法当原来可以拖曳的边缘被锁定不可拖曳时回调.如果边缘在初始化开始拖曳前被拒绝拖曳,就会发生前面说的这种情况.
         * 但这个方法会在{@link #onEdgeTouched(int, int)}之后才会被回调.这个方法会返回true来锁定该边缘.或者
         * 返回false来释放解锁该屏幕.默认的行为是后者(返回false来释放解锁该屏幕).
         * 
         * Called when the given edge may become locked. This can happen if an edge drag
         * was preliminarily rejected before beginning, but after {@link #onEdgeTouched(int, int)}
         * was called. This method should return true to lock this edge or false to leave it
         * unlocked. The default behavior is to leave edges unlocked.
         *
         * @param edgeFlags A combination of edge flags describing the edge(s) locked
         * 		- 描述被锁定的边缘的边缘标记,如EDGE_LEFT等.
         * @return true to lock the edge, false to leave it unlocked
         * 		- 返回true来锁定该边缘.或者 返回false来释放解锁该屏幕.
         */
        public boolean onEdgeLock(int edgeFlags) {
            return false;
        }

        /**
         * 当用户开始从父view中"订阅的"(之前约定允许拖曳的)屏幕边缘拖曳,并且父view中没有子view响应时调用.
         * 
         * Called when the user has started a deliberate drag away from one
         * of the subscribed edges in the parent view while no child view is currently captured.
         *
         * @param edgeFlags A combination of edge flags describing the edge(s) dragged
         * 		- 描述该边缘的边缘标记,如EDGE_LEFT等.
         * @param pointerId ID of the pointer touching the described edge(s)
         * 		- pointer的ID.
         * @see #EDGE_LEFT
         * @see #EDGE_TOP
         * @see #EDGE_RIGHT
         * @see #EDGE_BOTTOM
         */
        public void onEdgeDragStarted(int edgeFlags, int pointerId) {}

        /**
         * 调用设置子view z轴次序的参数.
         * Called to determine the Z-order of child views.
         *
         * @param index the ordered position to query for
         * @return index of the view that should be ordered at position <code>index</code>
         */
        public int getOrderedChildIndex(int index) {
            return index;
        }

        /**
         * 返回拖曳的子view水平移动范围的值,单位为px.这个方法如果返回0,那么该view则不能水平移动.
         * Return the magnitude of a draggable child view's horizontal range of motion in pixels.
         * This method should return 0 for views that cannot move horizontally.
         *
         * @param child Child view to check - 目标子view
         * @return range of horizontal motion in pixels	- 水平拖曳的值,单位为px.
         */
        public int getViewHorizontalDragRange(View child) {
            return 0;
        }

        /**
         * 返回拖曳的子view垂直移动范围的值,单位为px.这个方法如果返回0,那么该view则不能垂直移动.
         * Return the magnitude of a draggable child view's vertical range of motion in pixels.
         * This method should return 0 for views that cannot move vertically.
         *
         * @param child Child view to check
         * @return range of vertical motion in pixels
         */
        public int getViewVerticalDragRange(View child) {
            return 0;
        }

        /**
         * 当用户通过pointerId 输入特定值令目标子view移动时回调该方法.callback接口如果返回true,则表示用户
         * 允许通过用于引导的pointer来拖曳该子view.
         * Called when the user's input indicates that they want to capture the given child view
         * with the pointer indicated by pointerId. The callback should return true if the user
         * is permitted to drag the given view with the indicated pointer.
         *
         * 如果该子view已经被捕获, ViewDragHelper可能多次重复的调用该方法.多次的调用会导致新的pointer尝试去控制这个view.
         * <p>ViewDragHelper may call this method multiple times for the same view even if
         * the view is already captured; this indicates that a new pointer is trying to take
         * control of the view.</p>
         *
         * 如果该方法返回true,并且当成功捕获到该子view时,方法{@link #onViewCaptured(android.view.View, int)}会随即被调用.
         * <p>If this method returns true, a call to {@link #onViewCaptured(android.view.View, int)}
         * will follow if the capture is successful.</p>
         *
         * @param child Child the user is attempting to capture	- 用户视图捕获的子view
         * @param pointerId ID of the pointer attempting the capture	- 捕获该子view的pointerID.
         * @return true if capture should be allowed, false otherwise	- 如果允许并且捕获成功应该返回true.否则返回false.
         */
        public abstract boolean tryCaptureView(View child, int pointerId);

        /**
         * 该方法用于限制子view沿水平拖曳的手势.默认的实现是,不允许水平手势.如果有类继承了该类,
         * 必须覆盖重写该方法,并且提供值去限制该拖曳手势.
         * Restrict the motion of the dragged child view along the horizontal axis.
         * The default implementation does not allow horizontal motion; the extending
         * class must override this method and provide the desired clamping.
         *
         *
         * @param child Child view being dragged	-  被拖曳的子view.
         * @param left Attempted motion along the X axis	- 沿X轴(水平)的手势
         * @param dx Proposed change in position for left	- view的left变更值
         * @return The new clamped position for left - 对left返回新的位置值
         */
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            return 0;
        }

        /**
         * 该方法用于限制子view沿垂直拖曳的手势.默认的实现是,不允许垂直手势...(同上面的方法类似,就不过多解释了.)
         * Restrict the motion of the dragged child view along the vertical axis.
         * The default implementation does not allow vertical motion; the extending
         * class must override this method and provide the desired clamping.
         *
         *
         * @param child Child view being dragged
         * @param top Attempted motion along the Y axis
         * @param dy Proposed change in position for top
         * @return The new clamped position for top
         */
        public int clampViewPositionVertical(View child, int top, int dy) {
            return 0;
        }
    }

    /**
     * 定义曲线动画的插值器
     * Interpolator defining the animation curve for mScroller
     */
    private static final Interpolator sInterpolator = new Interpolator() {
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    // 实现Runnable接口
    private final Runnable mSetIdleRunnable = new Runnable() {
        public void run() {
            setDragState(STATE_IDLE);
        }
    };

    /**
     * 创建ViewDragHelper的工厂方法
     * Factory method to create a new ViewDragHelper.
     *
     * @param forParent Parent view to monitor  - 所要监听的父view
     * @param cb Callback to provide information and receive events - 提供信息的Callback对象
     * @return a new ViewDragHelper instance
     */
    public static ViewDragHelper create(ViewGroup forParent, Callback cb) {
        return new ViewDragHelper(forParent.getContext(), forParent, cb);
    }

    /**
     * Factory method to create a new ViewDragHelper.
     *
     * @param forParent Parent view to monitor
     * @param sensitivity Multiplier for how sensitive the helper should be about detecting
     *                    the start of a drag. Larger values are more sensitive. 1.0f is normal.
     * @param cb Callback to provide information and receive events
     * @return a new ViewDragHelper instance
     */
    public static ViewDragHelper create(ViewGroup forParent, float sensitivity, Callback cb) {
        final ViewDragHelper helper = create(forParent, cb);
        helper.mTouchSlop = (int) (helper.mTouchSlop * (1 / sensitivity));
        return helper;
    }

    /**
     * 应用应该使用ViewDragHelper.create()去获取新的实例.这将允许ViewDragHelper使用内部实现去兼容不同的平台版本.
     * Apps should use ViewDragHelper.create() to get a new instance.
     * This will allow VDH to use internal compatibility implementations for different
     * platform versions.
     *
     * @param context Context to initialize config-dependent params from
     * @param forParent Parent view to monitor
     */
    private ViewDragHelper(Context context, ViewGroup forParent, Callback cb) {
        if (forParent == null) {
            throw new IllegalArgumentException("Parent view may not be null");
        }
        if (cb == null) {
            throw new IllegalArgumentException("Callback may not be null");
        }

        mParentView = forParent;
        mCallback = cb;

        // ViewConfiguration是一个包含配置信息,如时间,位移等的配置类.
        final ViewConfiguration vc = ViewConfiguration.get(context);
        final float density = context.getResources().getDisplayMetrics().density;
        mEdgeSize = (int) (EDGE_SIZE * density + 0.5f);

        mTouchSlop = vc.getScaledTouchSlop();
        mMaxVelocity = vc.getScaledMaximumFlingVelocity();
        mMinVelocity = vc.getScaledMinimumFlingVelocity();
        mScroller = ScrollerCompat.create(context, sInterpolator);
    }

    /**
     * 设置最小速率.大于0px/s的速率能更好的被检测到.这样Callback就能恰当的运用该值去约束移动的速率.
     * Set the minimum velocity that will be detected as having a magnitude greater than zero
     * in pixels per second. Callback methods accepting a velocity will be clamped appropriately.
     *
     * @param minVel Minimum velocity to detect
     */
    public void setMinVelocity(float minVel) {
        mMinVelocity = minVel;
    }

    /**
     * 获取最小速率. 值得注意的是,如果最小速率小于0, 那么直接返回0,不会返回比0小的值.
     * Return the currently configured minimum velocity. Any flings with a magnitude less
     * than this value in pixels per second. Callback methods accepting a velocity will receive
     * zero as a velocity value if the real detected velocity was below this threshold.
     *
     * @return the minimum velocity that will be detected
     */
    public float getMinVelocity() {
        return mMinVelocity;
    }

    /**
     * 获取当前helper的拖曳状态,返回结果为{@link #STATE_IDLE}, {@link #STATE_DRAGGING} 
     * or {@link #STATE_SETTLING}.中的其一.
     * 
     * Retrieve the current drag state of this helper. This will return one of
     * {@link #STATE_IDLE}, {@link #STATE_DRAGGING} or {@link #STATE_SETTLING}.
     * @return The current drag state
     */
    public int getViewDragState() {
        return mDragState;
    }

    /**
     * 设置允许父view的某个边缘可追踪.CallBack对象的{@link Callback#onEdgeTouched(int, int)} and
     * {@link Callback#onEdgeDragStarted(int, int)}方法只有在边缘允许被追踪时才会调用. 
     * (就是说,如果不设置上下左右的某个边缘可追踪,那么这2个方法是不可用的.)
     * 
     * Enable edge tracking for the selected edges of the parent view.
     * The callback's {@link Callback#onEdgeTouched(int, int)} and
     * {@link Callback#onEdgeDragStarted(int, int)} methods will only be invoked
     * for edges for which edge tracking has been enabled.
     *
     * @param edgeFlags Combination of edge flags describing the edges to watch
     * @see #EDGE_LEFT
     * @see #EDGE_TOP
     * @see #EDGE_RIGHT
     * @see #EDGE_BOTTOM
     */
    public void setEdgeTrackingEnabled(int edgeFlags) {
        mTrackingEdges = edgeFlags;
    }

    /**
     * 返回边缘大小的值.单位为px.这个值是该view边缘可以被监测或追踪的值的范围.
     * Return the size of an edge. This is the range in pixels along the edges of this view
     * that will actively detect edge touches or drags if edge tracking is enabled.
     *
     * @return The size of an edge in pixels
     * @see #setEdgeTrackingEnabled(int)
     */
    public int getEdgeSize() {
        return mEdgeSize;
    }

    /**
     * 在父view内捕获指定的子view用于拖曳.同时callback对象会被通知.但{@link Callback#tryCaptureView(android.view.View, int)}
     * 不会被要求获取权限来捕获该view.
     * 
     * Capture a specific child view for dragging within the parent. The callback will be notified
     * but {@link Callback#tryCaptureView(android.view.View, int)} will not be asked permission to
     * capture this view.
     *
     * @param childView Child view to capture
     * @param activePointerId ID of the pointer that is dragging the captured child view
     */
    public void captureChildView(View childView, int activePointerId) {
        if (childView.getParent() != mParentView) {
            throw new IllegalArgumentException("captureChildView: parameter must be a descendant " +
                    "of the ViewDragHelper's tracked parent view (" + mParentView + ")");
        }

        mCapturedView = childView;
        mActivePointerId = activePointerId;
        mCallback.onViewCaptured(childView, activePointerId);
        setDragState(STATE_DRAGGING);
    }

    /**
     * 返回当前捕获的view.如果没有捕获到的view,则返回null.
     * @return The currently captured view, or null if no view has been captured.
     */
    public View getCapturedView() {
        return mCapturedView;
    }

    /**
     * 当前拖曳捕获的view的点(pointer)的ID.
     * @return The ID of the pointer currently dragging the captured view,
     *         or {@link #INVALID_POINTER}.
     */
    public int getActivePointerId() {
        return mActivePointerId;
    }

    /**
     * 获取最小触发和初始化拖曳动作的值,单位px.
     * @return The minimum distance in pixels that the user must travel to initiate a drag
     */
    public int getTouchSlop() {
        return mTouchSlop;
    }

    /**
     * 这方法等价于onTouch中MotionEvent的ACTION_CANCEL事件.
     * The result of a call to this method is equivalent to
     * {@link #processTouchEvent(android.view.MotionEvent)} receiving an ACTION_CANCEL event.
     */
    public void cancel() {
        mActivePointerId = INVALID_POINTER;
        clearMotionHistory();

        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /**
     * 中止取所有手势.并且直接结束动画.
     * {@link #cancel()}, but also abort all motion in progress and snap to the end of any
     * animation.
     */
    public void abort() {
        cancel();
        if (mDragState == STATE_SETTLING) {
            final int oldX = mScroller.getCurrX();
            final int oldY = mScroller.getCurrY();
            mScroller.abortAnimation();
            final int newX = mScroller.getCurrX();
            final int newY = mScroller.getCurrY();
            mCallback.onViewPositionChanged(mCapturedView, newX, newY, newX - oldX, newY - oldY);
        }
        //中止了,当然要设置拖曳状态为闲置(或者说初始态)
        setDragState(STATE_IDLE);
    }

    /**
     * (使用这个方法,可以有动画效果的移动子view到特定位置,该位置需要给出的finalLeft和 finalTop值.)
     * 随着动画,子view移动到既定(给定left和top值)的位置.如果这个方法返回true,会在后面随着手势移动的
     * 每一帧中回调{@link #continueSettling(boolean)}方法,直至返回false.如果这个方法返回false,
     * 就不会再移动去完成手势动作的事件. 
     * 
     * Animate the view <code>child</code> to the given (left, top) position.
     * If this method returns true, the caller should invoke {@link #continueSettling(boolean)}
     * on each subsequent frame to continue the motion until it returns false. If this method
     * returns false there is no further work to do to complete the movement.
     *
     * 要注意的是,即使方法{@link #getCapturedView()}在这个滑动过程中仍会一直有效,可以获取catureView的值,
     * 但这个操作过程不看做是一个捕获事件(我们应当知道,捕获子view不是我们决定的,是Helper自动在父view和
     * 子view之间去自动完成的过程,无论这个过程成功还是失败).
     * 
     * <p>This operation does not count as a capture event, though {@link #getCapturedView()}
     * will still report the sliding view while the slide is in progress.</p>
     *
     * @param child Child view to capture and animate - 要捕获和添加动画移动的view对象
     * @param finalLeft Final left position of child - 最终位置的left值
     * @param finalTop Final top position of child - 最终位置的top值
     * @return true if animation should continue through {@link #continueSettling(boolean)} calls
     */
    public boolean smoothSlideViewTo(View child, int finalLeft, int finalTop) {
        mCapturedView = child;
        mActivePointerId = INVALID_POINTER;

        boolean continueSliding = forceSettleCapturedViewAt(finalLeft, finalTop, 0, 0);
        if (!continueSliding && mDragState == STATE_IDLE && mCapturedView != null) {
            // If we're in an IDLE state to begin with and aren't moving anywhere, we
            // end up having a non-null capturedView with an IDLE dragState
            mCapturedView = null;
        }

        return continueSliding;
    }

    /**
     * (通过这个方法,我们应当知道settle和slide的区别.前者是直接跳到结束位置,而后者是有过渡效果的.)
     * 将捕获的view设置(settle)在给定的left,top值的位置.(表示,直接忽略过程,直接将view显示在特定位置)
     * 这个过程中,该view(如果在此时已经有)适当的速度,则该速度会影响settle的过程.
     * 如果这个方法返回true,方法{@link #continueSettling(boolean)}在整个settle过程中会被回调,直至返回false.
     * 如果这个方法返回false,(表示此时该view已经在给定的位置)这个settle的过程就会结束,不会再工作完成事件.
     * 
     * Settle the captured view at the given (left, top) position.
     * The appropriate velocity from prior motion will be taken into account.
     * If this method returns true, the caller should invoke {@link #continueSettling(boolean)}
     * on each subsequent frame to continue the motion until it returns false. If this method
     * returns false there is no further work to do to complete the movement.
     *
     * @param finalLeft Settled left edge position for the captured view
     * @param finalTop Settled top edge position for the captured view
     * @return true if animation should continue through {@link #continueSettling(boolean)} calls
     */
    public boolean settleCapturedViewAt(int finalLeft, int finalTop) {
        if (!mReleaseInProgress) {
            throw new IllegalStateException("Cannot settleCapturedViewAt outside of a call to " +
                    "Callback#onViewReleased");
        }

        return forceSettleCapturedViewAt(finalLeft, finalTop,
                (int) VelocityTrackerCompat.getXVelocity(mVelocityTracker, mActivePointerId),
                (int) VelocityTrackerCompat.getYVelocity(mVelocityTracker, mActivePointerId));
    }

    /**
     * 同样是将view直接设到特定位置(给定left, top值).
     * (看该方法的实现,整个过程 也是靠scroller的scroll去实现的).
     * Settle the captured view at the given (left, top) position.
     *
     * @param finalLeft Target left position for the captured view
     * @param finalTop Target top position for the captured view
     * @param xvel Horizontal velocity	- 水平速度
     * @param yvel Vertical velocity	- 垂直速度
     * @return true if animation should continue through {@link #continueSettling(boolean)} calls
     * 		- settleing的过程中会一直返回true,否则返回false表示结束.
     */
    private boolean forceSettleCapturedViewAt(int finalLeft, int finalTop, int xvel, int yvel) {
        final int startLeft = mCapturedView.getLeft();
        final int startTop = mCapturedView.getTop();
        final int dx = finalLeft - startLeft;
        final int dy = finalTop - startTop;

        if (dx == 0 && dy == 0) {
            // Nothing to do. Send callbacks, be done.
            mScroller.abortAnimation();
            setDragState(STATE_IDLE);
            return false;
        }
        // 仔细看computeSettleDuration()这个计算时间的方法,其实挺复杂的.使用了相当多的运算处理.因此可以不看该方法的实现;
        // 除非要继承ViewDrarHelper实现子类,实现更多效果...
        final int duration = computeSettleDuration(mCapturedView, dx, dy, xvel, yvel);
        mScroller.startScroll(startLeft, startTop, dx, dy, duration);

        setDragState(STATE_SETTLING);
        return true;
    }

    //该方法计算settle的时间
    private int computeSettleDuration(View child, int dx, int dy, int xvel, int yvel) {
    	//clampMag(...)方法保证水平和垂直速度值不大于最大值, 也不小于最小值.
        xvel = clampMag(xvel, (int) mMinVelocity, (int) mMaxVelocity);
        yvel = clampMag(yvel, (int) mMinVelocity, (int) mMaxVelocity);
        final int absDx = Math.abs(dx);
        final int absDy = Math.abs(dy);
        final int absXVel = Math.abs(xvel);
        final int absYVel = Math.abs(yvel);
        final int addedVel = absXVel + absYVel;
        final int addedDistance = absDx + absDy;

        final float xweight = xvel != 0 ? (float) absXVel / addedVel :
                (float) absDx / addedDistance;
        final float yweight = yvel != 0 ? (float) absYVel / addedVel :
                (float) absDy / addedDistance;
        //要注意的是getViewHorizontalDragRange(...)方法默认返回0,但一般都会在创建helper时传进的mCallback中重写该方法
        int xduration = computeAxisDuration(dx, xvel, mCallback.getViewHorizontalDragRange(child));
        int yduration = computeAxisDuration(dy, yvel, mCallback.getViewVerticalDragRange(child));

        return (int) (xduration * xweight + yduration * yweight);
    }
    
    //该方法计算settle的时间,三个输入的参数依次分别是:水平或垂直方向的移动距离,水平或垂直方向的速度大小,拖曳范围值
    private int computeAxisDuration(int delta, int velocity, int motionRange) {
        if (delta == 0) {
            return 0;
        }

        final int width = mParentView.getWidth();
        final int halfWidth = width / 2;
        final float distanceRatio = Math.min(1f, (float) Math.abs(delta) / width);
        final float distance = halfWidth + halfWidth *
                distanceInfluenceForSnapDuration(distanceRatio);

        int duration;
        velocity = Math.abs(velocity);
        if (velocity > 0) {
            duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
        } else {
            final float range = (float) Math.abs(delta) / motionRange;
            duration = (int) ((range + 1) * BASE_SETTLE_DURATION);
        }
        return Math.min(duration, MAX_SETTLE_DURATION);
    }

    /**
     * 该方法通过最大和最小值,算出区间值.低于最小值返回0,大于最大值则返回最大值.
     * Clamp the magnitude of value for absMin and absMax.
     * If the value is below the minimum, it will be clamped to zero.
     * If the value is above the maximum, it will be clamped to the maximum.
     *
     * @param value Value to clamp
     * @param absMin Absolute value of the minimum significant value to return
     * @param absMax Absolute value of the maximum value to return
     * @return The clamped value with the same sign as <code>value</code>
     */
    private int clampMag(int value, int absMin, int absMax) {
        final int absValue = Math.abs(value);
        if (absValue < absMin) return 0;
        if (absValue > absMax) return value > 0 ? absMax : -absMax;
        return value;
    }

    /**
     * 这个方法和上面的clampMag(int value, int absMin, int absMax)几乎一样,只是换了浮点型.
     * Clamp the magnitude of value for absMin and absMax.
     * If the value is below the minimum, it will be clamped to zero.
     * If the value is above the maximum, it will be clamped to the maximum.
     *
     * @param value Value to clamp
     * @param absMin Absolute value of the minimum significant value to return
     * @param absMax Absolute value of the maximum value to return
     * @return The clamped value with the same sign as <code>value</code>
     */
    private float clampMag(float value, float absMin, float absMax) {
        final float absValue = Math.abs(value);
        if (absValue < absMin) return 0;
        if (absValue > absMax) return value > 0 ? absMax : -absMax;
        return value;
    }

    private float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    /**
     * 该方法类似上面的forceSettleCapturedViewAt(...),可参考之.
     * 
     * Settle the captured view based on standard free-moving fling behavior.
     * The caller should invoke {@link #continueSettling(boolean)} on each subsequent frame
     * to continue the motion until it returns false.
     *
     * @param minLeft Minimum X position for the view's left edge
     * @param minTop Minimum Y position for the view's top edge
     * @param maxLeft Maximum X position for the view's left edge
     * @param maxTop Maximum Y position for the view's top edge
     */
    public void flingCapturedView(int minLeft, int minTop, int maxLeft, int maxTop) {
        if (!mReleaseInProgress) {
            throw new IllegalStateException("Cannot flingCapturedView outside of a call to " +
                    "Callback#onViewReleased");
        }

        mScroller.fling(mCapturedView.getLeft(), mCapturedView.getTop(),
                (int) VelocityTrackerCompat.getXVelocity(mVelocityTracker, mActivePointerId),
                (int) VelocityTrackerCompat.getYVelocity(mVelocityTracker, mActivePointerId),
                minLeft, maxLeft, minTop, maxTop);

        setDragState(STATE_SETTLING);
    }

    /**
     * 这个方法在上面好几地方都被提及了.
     * 在整个settle的过程中,这个方法会返回true.直至返回false,表示settle的过程结束.
     * (该方法是内部调用的,外部建议不适用.)
     * 
     * Move the captured settling view by the appropriate amount for the current time.
     * If <code>continueSettling</code> returns true, the caller should call it again
     * on the next frame to continue.
     *
     * 参数deferCallbacks - 如果要推迟滑动,比如在{@link android.view.View#computeScroll()}里面回调,或者view还在layout或者draw
     * 的过程中,该参数应当传true;
     * @param deferCallbacks true if state callbacks should be deferred via posted message.
     *                       Set this to true if you are calling this method from
     *                       {@link android.view.View#computeScroll()} or similar methods
     *                       invoked as part of layout or drawing.
     * @return true if settle is still in progress
     */
    public boolean continueSettling(boolean deferCallbacks) {
        if (mDragState == STATE_SETTLING) {
        	// 由于整个settle的过程都借助Scroller去实现,
            // 因此keepGoing这个值也来自mScroller.computeScrollOffset();
        	// mScroller.computeScrollOffset()这方法,表示只要view处于scroll状态,都会返回true.停止scroll则返回false.
            boolean keepGoing = mScroller.computeScrollOffset();
            final int x = mScroller.getCurrX();
            final int y = mScroller.getCurrY();
            final int dx = x - mCapturedView.getLeft();
            final int dy = y - mCapturedView.getTop();

            if (dx != 0) {
                mCapturedView.offsetLeftAndRight(dx);
            }
            if (dy != 0) {
                mCapturedView.offsetTopAndBottom(dy);
            }

            if (dx != 0 || dy != 0) {
            	// 可见该方法在整个settle的过程中,由于位置的不断变化
            	// 会一直回调mCallback.onViewPositionChanged(...)的方法
                mCallback.onViewPositionChanged(mCapturedView, x, y, dx, dy);
            }
            
            //这里很明显,当view已经去到最终位置,XY的坐标均相等时,即使keepGoing依然为true,系统以为
            //该view依旧处于滑动中,但很显然,应该结束了.于是方法里面强制调用Scroller.abortAnimation()去中止动画,并
            //向mScroller标记完成状态.keepGoing自然就为false了.
            if (keepGoing && x == mScroller.getFinalX() && y == mScroller.getFinalY()) {
                // Close enough. The interpolator/scroller might think we're still moving
                // but the user sure doesn't.
                mScroller.abortAnimation();
                keepGoing = false;
            }
            
            //此处推迟滑动,借助Runable接口去实现
            if (!keepGoing) {
                if (deferCallbacks) {
                    mParentView.post(mSetIdleRunnable);
                } else {
                	//来到这里,keepGoing和deferCallbacks为false,表示整个settle过程都结束了.
                	//更改拖曳状态,continueSettling(...)不会再被回调.
                    setDragState(STATE_IDLE);
                }
            }
        }

        return mDragState == STATE_SETTLING;
    }

    /**
     * (该方法是当完成settle过程后释放捕获到的view对象, 内部方法,不必了解详细过程.)
     * 正如所有接口事件的方法,这个方法也必须在UI主线程中使用.在释放的过程中,只会调用一次
     * {@link #settleCapturedViewAt(int, int)}或者{@link #flingCapturedView(int, int, int, int)}方法.
     * 
     * Like all callback events this must happen on the UI thread, but release
     * involves some extra semantics. During a release (mReleaseInProgress)
     * is the only time it is valid to call {@link #settleCapturedViewAt(int, int)}
     * or {@link #flingCapturedView(int, int, int, int)}.
     */
    private void dispatchViewReleased(float xvel, float yvel) {
        mReleaseInProgress = true;
        mCallback.onViewReleased(mCapturedView, xvel, yvel);
        mReleaseInProgress = false;

        if (mDragState == STATE_DRAGGING) {
            // onViewReleased didn't call a method that would have changed this. Go idle.
            setDragState(STATE_IDLE);
        }
    }

    //下面几个"clear"为首的方法都是清空历史记录了
    private void clearMotionHistory() {
        if (mInitialMotionX == null) {
            return;
        }
        Arrays.fill(mInitialMotionX, 0);
        Arrays.fill(mInitialMotionY, 0);
        Arrays.fill(mLastMotionX, 0);
        Arrays.fill(mLastMotionY, 0);
        Arrays.fill(mInitialEdgesTouched, 0);
        Arrays.fill(mEdgeDragsInProgress, 0);
        Arrays.fill(mEdgeDragsLocked, 0);
        mPointersDown = 0;
    }

    private void clearMotionHistory(int pointerId) {
        if (mInitialMotionX == null) {
            return;
        }
        mInitialMotionX[pointerId] = 0;
        mInitialMotionY[pointerId] = 0;
        mLastMotionX[pointerId] = 0;
        mLastMotionY[pointerId] = 0;
        mInitialEdgesTouched[pointerId] = 0;
        mEdgeDragsInProgress[pointerId] = 0;
        mEdgeDragsLocked[pointerId] = 0;
        mPointersDown &= ~(1 << pointerId);
    }
    
    //这个方法很明显是内部调用的,在saveInitialMotion(...)中调用.
    //因为mInitialMotionX数组里面保存有触摸X坐标的缓存信息,该方法确保mInitialMotionX一直保存最新的pointerId值
    private void ensureMotionHistorySizeForId(int pointerId) {
        if (mInitialMotionX == null || mInitialMotionX.length <= pointerId) {
            float[] imx = new float[pointerId + 1];
            float[] imy = new float[pointerId + 1];
            float[] lmx = new float[pointerId + 1];
            float[] lmy = new float[pointerId + 1];
            int[] iit = new int[pointerId + 1];
            int[] edip = new int[pointerId + 1];
            int[] edl = new int[pointerId + 1];
            
            //这个过程,将触摸的X,Y坐标,上次触摸的X,Y坐标等信息复制过去
            if (mInitialMotionX != null) {
            	//这里调用本地C方法去将mInitialMotionX的内存复制给imx数组,没源码...
                System.arraycopy(mInitialMotionX, 0, imx, 0, mInitialMotionX.length);
                System.arraycopy(mInitialMotionY, 0, imy, 0, mInitialMotionY.length);
                System.arraycopy(mLastMotionX, 0, lmx, 0, mLastMotionX.length);
                System.arraycopy(mLastMotionY, 0, lmy, 0, mLastMotionY.length);
                System.arraycopy(mInitialEdgesTouched, 0, iit, 0, mInitialEdgesTouched.length);
                System.arraycopy(mEdgeDragsInProgress, 0, edip, 0, mEdgeDragsInProgress.length);
                System.arraycopy(mEdgeDragsLocked, 0, edl, 0, mEdgeDragsLocked.length);
            }

            mInitialMotionX = imx;
            mInitialMotionY = imy;
            mLastMotionX = lmx;
            mLastMotionY = lmy;
            mInitialEdgesTouched = iit;
            mEdgeDragsInProgress = edip;
            mEdgeDragsLocked = edl;
        }
    }

    // 在这里,连同pointerId,保存X,Y轴坐标信息
    // 也许看到这里,你已经猜到,pointerId这个值是递增的,由系统自动分配.
    private void saveInitialMotion(float x, float y, int pointerId) {
        ensureMotionHistorySizeForId(pointerId);
        mInitialMotionX[pointerId] = mLastMotionX[pointerId] = x;
        mInitialMotionY[pointerId] = mLastMotionY[pointerId] = y;
        mInitialEdgesTouched[pointerId] = getEdgesTouched((int) x, (int) y);
        // 或运算后再进行左移运算. 
        mPointersDown |= 1 << pointerId;
    }

    private void saveLastMotion(MotionEvent ev) {
        final int pointerCount = MotionEventCompat.getPointerCount(ev);
        for (int i = 0; i < pointerCount; i++) {
            final int pointerId = MotionEventCompat.getPointerId(ev, i);
            final float x = MotionEventCompat.getX(ev, i);
            final float y = MotionEventCompat.getY(ev, i);
            mLastMotionX[pointerId] = x;
            mLastMotionY[pointerId] = y;
        }
    }

    /**
     * 检查给定id的pointer是否当前按下的pointer.
     * Check if the given pointer ID represents a pointer that is currently down (to the best
     * of the ViewDragHelper's knowledge).
     *
     * 被用于报告这个pointer信息的有以下几个方法:shouldInterceptTouchEvent()和processTouchEvent().
     * 如果这其中一个方法都没有被相关的触摸事件回调,那么该方法中所汇报的信息是不准确或者过时的.
     * (很明显,最新的触摸信息,必须是当前InterceptTouchEvent事件中能回调的.)
     * 
     * <p>The state used to report this information is populated by the methods
     * {@link #shouldInterceptTouchEvent(android.view.MotionEvent)} or
     * {@link #processTouchEvent(android.view.MotionEvent)}. If one of these methods has not
     * been called for all relevant MotionEvents to track, the information reported
     * by this method may be stale or incorrect.</p>
     *
     * @param pointerId pointer ID to check; corresponds to IDs provided by MotionEvent
     * @return true if the pointer with the given ID is still down
     */
    public boolean isPointerDown(int pointerId) {
        return (mPointersDown & 1 << pointerId) != 0;
    }

    void setDragState(int state) {
        mParentView.removeCallbacks(mSetIdleRunnable);
        if (mDragState != state) {
            mDragState = state;
            mCallback.onViewDragStateChanged(state);
            if (mDragState == STATE_IDLE) {
                mCapturedView = null;
            }
        }
    }

    /**
     * 通过传进的pointerId,试图捕获view.如果之前已成功捕获过,则不再调用mCallback.tryCaptureView()方法,而直接返回true.
     * Attempt to capture the view with the given pointer ID. The callback will be involved.
     * This will put us into the "dragging" state. If we've already captured this view with
     * this pointer this method will immediately return true without consulting the callback.
     *
     * @param toCapture View to capture
     * @param pointerId Pointer to capture with
     * @return true if capture was successful
     */
    boolean tryCaptureViewForDrag(View toCapture, int pointerId) {
        if (toCapture == mCapturedView && mActivePointerId == pointerId) {
            // Already done!
            return true;
        }
        if (toCapture != null && mCallback.tryCaptureView(toCapture, pointerId)) {
            mActivePointerId = pointerId;
            captureChildView(toCapture, pointerId);
            return true;
        }
        return false;
    }

    /**
     * 测试是否view v是否能滑动
     * Tests scrollability within child views of v given a delta of dx.
     *
     * @param v View to test for horizontal scrollability
     * @param checkV Whether the view v passed should itself be checked for scrollability (true),
     *               or just its children (false).
     * @param dx Delta scrolled in pixels along the X axis
     * @param dy Delta scrolled in pixels along the Y axis
     * @param x X coordinate of the active touch point
     * @param y Y coordinate of the active touch point
     * @return true if child views of v can be scrolled by delta of dx.
     */
    protected boolean canScroll(View v, boolean checkV, int dx, int dy, int x, int y) {
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            final int scrollX = v.getScrollX();
            final int scrollY = v.getScrollY();
            final int count = group.getChildCount();
            // Count backwards - let topmost views consume scroll distance first.
            for (int i = count - 1; i >= 0; i--) {
                // TODO: Add versioned support here for transformed views.
                // This will not work for transformed views in Honeycomb+
                final View child = group.getChildAt(i);
                if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight() &&
                        y + scrollY >= child.getTop() && y + scrollY < child.getBottom() &&
                        canScroll(child, true, dx, dy, x + scrollX - child.getLeft(),
                                y + scrollY - child.getTop())) {
                    return true;
                }
            }
        }

        return checkV && (ViewCompat.canScrollHorizontally(v, -dx) ||
                ViewCompat.canScrollVertically(v, -dy));
    }

    /**
     * 检测这个作为被提供给父view的onInterceptTouchEvent的事件是否令父view拦截到当前的触摸事件流.
     * Check if this event as provided to the parent view's onInterceptTouchEvent should
     * cause the parent to intercept the touch event stream.
     *
     * @param ev MotionEvent provided to onInterceptTouchEvent - 提供给onInterceptTouchEvent()方法的触摸事件对象
     * @return true if the parent view should return true from onInterceptTouchEvent
     */
    public boolean shouldInterceptTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);
        final int actionIndex = MotionEventCompat.getActionIndex(ev);

        if (action == MotionEvent.ACTION_DOWN) {
            // Reset things for a new event stream, just in case we didn't get
            // the whole previous stream.
            cancel();
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                final int pointerId = MotionEventCompat.getPointerId(ev, 0);
                saveInitialMotion(x, y, pointerId);

                final View toCapture = findTopChildUnder((int) x, (int) y);

                // Catch a settling view if possible.
                if (toCapture == mCapturedView && mDragState == STATE_SETTLING) {
                    tryCaptureViewForDrag(toCapture, pointerId);
                }

                final int edgesTouched = mInitialEdgesTouched[pointerId];
                if ((edgesTouched & mTrackingEdges) != 0) {
                    mCallback.onEdgeTouched(edgesTouched & mTrackingEdges, pointerId);
                }
                break;
            }

            case MotionEventCompat.ACTION_POINTER_DOWN: {
                final int pointerId = MotionEventCompat.getPointerId(ev, actionIndex);
                final float x = MotionEventCompat.getX(ev, actionIndex);
                final float y = MotionEventCompat.getY(ev, actionIndex);

                saveInitialMotion(x, y, pointerId);

                // A ViewDragHelper can only manipulate one view at a time.
                if (mDragState == STATE_IDLE) {
                    final int edgesTouched = mInitialEdgesTouched[pointerId];
                    if ((edgesTouched & mTrackingEdges) != 0) {
                        mCallback.onEdgeTouched(edgesTouched & mTrackingEdges, pointerId);
                    }
                } else if (mDragState == STATE_SETTLING) {
                    // Catch a settling view if possible.
                    final View toCapture = findTopChildUnder((int) x, (int) y);
                    if (toCapture == mCapturedView) {
                        tryCaptureViewForDrag(toCapture, pointerId);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                // First to cross a touch slop over a draggable view wins. Also report edge drags.
                final int pointerCount = MotionEventCompat.getPointerCount(ev);
                for (int i = 0; i < pointerCount; i++) {
                    final int pointerId = MotionEventCompat.getPointerId(ev, i);
                    final float x = MotionEventCompat.getX(ev, i);
                    final float y = MotionEventCompat.getY(ev, i);
                    final float dx = x - mInitialMotionX[pointerId];
                    final float dy = y - mInitialMotionY[pointerId];

                    final View toCapture = findTopChildUnder((int) x, (int) y);
                    final boolean pastSlop = toCapture != null && checkTouchSlop(toCapture, dx, dy);
                    if (pastSlop) {
                        // check the callback's
                        // getView[Horizontal|Vertical]DragRange methods to know
                        // if you can move at all along an axis, then see if it
                        // would clamp to the same value. If you can't move at
                        // all in every dimension with a nonzero range, bail.
                        final int oldLeft = toCapture.getLeft();
                        final int targetLeft = oldLeft + (int) dx;
                        final int newLeft = mCallback.clampViewPositionHorizontal(toCapture,
                                targetLeft, (int) dx);
                        final int oldTop = toCapture.getTop();
                        final int targetTop = oldTop + (int) dy;
                        final int newTop = mCallback.clampViewPositionVertical(toCapture, targetTop,
                                (int) dy);
                        final int horizontalDragRange = mCallback.getViewHorizontalDragRange(
                                toCapture);
                        final int verticalDragRange = mCallback.getViewVerticalDragRange(toCapture);
                        if ((horizontalDragRange == 0 || horizontalDragRange > 0
                                && newLeft == oldLeft) && (verticalDragRange == 0
                                || verticalDragRange > 0 && newTop == oldTop)) {
                            break;
                        }
                    }
                    reportNewEdgeDrags(dx, dy, pointerId);
                    if (mDragState == STATE_DRAGGING) {
                        // Callback might have started an edge drag
                        break;
                    }

                    if (pastSlop && tryCaptureViewForDrag(toCapture, pointerId)) {
                        break;
                    }
                }
                saveLastMotion(ev);
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP: {
                final int pointerId = MotionEventCompat.getPointerId(ev, actionIndex);
                clearMotionHistory(pointerId);
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                cancel();
                break;
            }
        }

        return mDragState == STATE_DRAGGING;
    }

    /**
     * 加工从父view中获取的触摸事件.这个方法将分发callback回调事件.父view的触摸事件实现中应该调用该方法.
     * Process a touch event received by the parent view. This method will dispatch callback events
     * as needed before returning. The parent view's onTouchEvent implementation should call this.
     *
     * @param ev The touch event received by the parent view
     */
    public void processTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);
        final int actionIndex = MotionEventCompat.getActionIndex(ev);

        if (action == MotionEvent.ACTION_DOWN) {
            // Reset things for a new event stream, just in case we didn't get
            // the whole previous stream.
            cancel();
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                final int pointerId = MotionEventCompat.getPointerId(ev, 0);
                final View toCapture = findTopChildUnder((int) x, (int) y);

                saveInitialMotion(x, y, pointerId);

                // Since the parent is already directly processing this touch event,
                // there is no reason to delay for a slop before dragging.
                // Start immediately if possible.
                tryCaptureViewForDrag(toCapture, pointerId);

                final int edgesTouched = mInitialEdgesTouched[pointerId];
                if ((edgesTouched & mTrackingEdges) != 0) {
                    mCallback.onEdgeTouched(edgesTouched & mTrackingEdges, pointerId);
                }
                break;
            }

            case MotionEventCompat.ACTION_POINTER_DOWN: {
                final int pointerId = MotionEventCompat.getPointerId(ev, actionIndex);
                final float x = MotionEventCompat.getX(ev, actionIndex);
                final float y = MotionEventCompat.getY(ev, actionIndex);

                saveInitialMotion(x, y, pointerId);

                // A ViewDragHelper can only manipulate one view at a time.
                if (mDragState == STATE_IDLE) {
                    // If we're idle we can do anything! Treat it like a normal down event.

                    final View toCapture = findTopChildUnder((int) x, (int) y);
                    tryCaptureViewForDrag(toCapture, pointerId);

                    final int edgesTouched = mInitialEdgesTouched[pointerId];
                    if ((edgesTouched & mTrackingEdges) != 0) {
                        mCallback.onEdgeTouched(edgesTouched & mTrackingEdges, pointerId);
                    }
                } else if (isCapturedViewUnder((int) x, (int) y)) {
                    // We're still tracking a captured view. If the same view is under this
                    // point, we'll swap to controlling it with this pointer instead.
                    // (This will still work if we're "catching" a settling view.)

                    tryCaptureViewForDrag(mCapturedView, pointerId);
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mDragState == STATE_DRAGGING) {
                    final int index = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                    final float x = MotionEventCompat.getX(ev, index);
                    final float y = MotionEventCompat.getY(ev, index);
                    final int idx = (int) (x - mLastMotionX[mActivePointerId]);
                    final int idy = (int) (y - mLastMotionY[mActivePointerId]);

                    dragTo(mCapturedView.getLeft() + idx, mCapturedView.getTop() + idy, idx, idy);

                    saveLastMotion(ev);
                } else {
                    // Check to see if any pointer is now over a draggable view.
                    final int pointerCount = MotionEventCompat.getPointerCount(ev);
                    for (int i = 0; i < pointerCount; i++) {
                        final int pointerId = MotionEventCompat.getPointerId(ev, i);
                        final float x = MotionEventCompat.getX(ev, i);
                        final float y = MotionEventCompat.getY(ev, i);
                        final float dx = x - mInitialMotionX[pointerId];
                        final float dy = y - mInitialMotionY[pointerId];

                        reportNewEdgeDrags(dx, dy, pointerId);
                        if (mDragState == STATE_DRAGGING) {
                            // Callback might have started an edge drag.
                            break;
                        }

                        final View toCapture = findTopChildUnder((int) x, (int) y);
                        if (checkTouchSlop(toCapture, dx, dy) &&
                                tryCaptureViewForDrag(toCapture, pointerId)) {
                            break;
                        }
                    }
                    saveLastMotion(ev);
                }
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP: {
                final int pointerId = MotionEventCompat.getPointerId(ev, actionIndex);
                if (mDragState == STATE_DRAGGING && pointerId == mActivePointerId) {
                    // Try to find another pointer that's still holding on to the captured view.
                    int newActivePointer = INVALID_POINTER;
                    final int pointerCount = MotionEventCompat.getPointerCount(ev);
                    for (int i = 0; i < pointerCount; i++) {
                        final int id = MotionEventCompat.getPointerId(ev, i);
                        if (id == mActivePointerId) {
                            // This one's going away, skip.
                            continue;
                        }

                        final float x = MotionEventCompat.getX(ev, i);
                        final float y = MotionEventCompat.getY(ev, i);
                        if (findTopChildUnder((int) x, (int) y) == mCapturedView &&
                                tryCaptureViewForDrag(mCapturedView, id)) {
                            newActivePointer = mActivePointerId;
                            break;
                        }
                    }

                    if (newActivePointer == INVALID_POINTER) {
                        // We didn't find another pointer still touching the view, release it.
                        releaseViewForPointerUp();
                    }
                }
                clearMotionHistory(pointerId);
                break;
            }

            case MotionEvent.ACTION_UP: {
                if (mDragState == STATE_DRAGGING) {
                    releaseViewForPointerUp();
                }
                cancel();
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                if (mDragState == STATE_DRAGGING) {
                    dispatchViewReleased(0, 0);
                }
                cancel();
                break;
            }
        }
    }
    
    //更新边缘拖曳标记
    private void reportNewEdgeDrags(float dx, float dy, int pointerId) {
        int dragsStarted = 0;
        if (checkNewEdgeDrag(dx, dy, pointerId, EDGE_LEFT)) {
            dragsStarted |= EDGE_LEFT;
        }
        if (checkNewEdgeDrag(dy, dx, pointerId, EDGE_TOP)) {
            dragsStarted |= EDGE_TOP;
        }
        if (checkNewEdgeDrag(dx, dy, pointerId, EDGE_RIGHT)) {
            dragsStarted |= EDGE_RIGHT;
        }
        if (checkNewEdgeDrag(dy, dx, pointerId, EDGE_BOTTOM)) {
            dragsStarted |= EDGE_BOTTOM;
        }

        if (dragsStarted != 0) {
            mEdgeDragsInProgress[pointerId] |= dragsStarted;
            mCallback.onEdgeDragStarted(dragsStarted, pointerId);
        }
    }
    
    //内部调用方法,计算是否新的拖曳标记
    private boolean checkNewEdgeDrag(float delta, float odelta, int pointerId, int edge) {
        final float absDelta = Math.abs(delta);
        final float absODelta = Math.abs(odelta);

        if ((mInitialEdgesTouched[pointerId] & edge) != edge  || (mTrackingEdges & edge) == 0 ||
                (mEdgeDragsLocked[pointerId] & edge) == edge ||
                (mEdgeDragsInProgress[pointerId] & edge) == edge ||
                (absDelta <= mTouchSlop && absODelta <= mTouchSlop)) {
            return false;
        }
        if (absDelta < absODelta * 0.5f && mCallback.onEdgeLock(edge)) {
            mEdgeDragsLocked[pointerId] |= edge;
            return false;
        }
        return (mEdgeDragsInProgress[pointerId] & edge) == 0 && absDelta > mTouchSlop;
    }

    /**
     * 检测在子view滑动时是否合理的出现溢出(这个现象是手指在滑动时部分滑动到屏幕外,但子view还能正常被拖曳移动).
     * 当溢出现象严重,子view就不能沿着手指滑动的轨迹在水平或垂直轴上被拖曳移动.这时,该轴上的手势将不计入检测溢出的考虑范围.
     * 
     * Check if we've crossed a reasonable touch slop for the given child view.
     * If the child cannot be dragged along the horizontal or vertical axis, motion
     * along that axis will not count toward the slop check.
     *
     * @param child Child to check - 要检测的子view
     * @param dx Motion since initial position along X axis - 从初始开始沿着X轴的坐标
     * @param dy Motion since initial position along Y axis	- - 从初始开始沿着Y的坐标
     * @return true if the touch slop has been crossed
     */
    private boolean checkTouchSlop(View child, float dx, float dy) {
        if (child == null) {
            return false;
        }
        final boolean checkHorizontal = mCallback.getViewHorizontalDragRange(child) > 0;
        final boolean checkVertical = mCallback.getViewVerticalDragRange(child) > 0;

        if (checkHorizontal && checkVertical) {
            return dx * dx + dy * dy > mTouchSlop * mTouchSlop;
        } else if (checkHorizontal) {
            return Math.abs(dx) > mTouchSlop;
        } else if (checkVertical) {
            return Math.abs(dy) > mTouchSlop;
        }
        return false;
    }

    /**
     * 检测当前手势是否的点是否超出了溢出的临界值.
     * 
     * Check if any pointer tracked in the current gesture has crossed
     * the required slop threshold.
     *
     * 这依赖于由方法shouldInterceptTouchEvent(...)或者processTouchEvent(...)所填充的内部状态.
     * 你只有在所有有效触摸的数据都被提供到这其中的一个方法时,才应该去使用这些方法返回的结果.
     *
     * <p>This depends on internal state populated by
     * {@link #shouldInterceptTouchEvent(android.view.MotionEvent)} or
     * {@link #processTouchEvent(android.view.MotionEvent)}. You should only rely on
     * the results of this method after all currently available touch data
     * has been provided to one of these two methods.</p>
     *
     * 参数directions - 指引值. 值为{@link #DIRECTION_HORIZONTAL},
     *                   {@link #DIRECTION_VERTICAL}, {@link #DIRECTION_ALL}中的其中一个.
     * @param directions Combination of direction flags, see {@link #DIRECTION_HORIZONTAL},
     *                   {@link #DIRECTION_VERTICAL}, {@link #DIRECTION_ALL}
     *                   
     * 如果返回true,表示超过了溢出的临界值.否则返回false.
     * @return true if the slop threshold has been crossed, false otherwise
     */
    public boolean checkTouchSlop(int directions) {
        final int count = mInitialMotionX.length;
        //mInitialMotionX装着触摸事件的所有坐标(所有pointer),因此循环去检验,只要有一个点超出临界值,就返回true.
        for (int i = 0; i < count; i++) {
            if (checkTouchSlop(directions, i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检测越出所需的溢出临界值的手势中,某个被追踪的具体的pointer.
     * 
     * Check if the specified pointer tracked in the current gesture has crossed
     * the required slop threshold.
     *
     * 这依赖于由方法shouldInterceptTouchEvent(...)或者processTouchEvent(...)所填充的内部状态.
     * 你只有在所有有效触摸的数据都被提供到这其中的一个方法时,才应该去使用这些方法返回的结果.
     * 
     * <p>This depends on internal state populated by
     * {@link #shouldInterceptTouchEvent(android.view.MotionEvent)} or
     * {@link #processTouchEvent(android.view.MotionEvent)}. You should only rely on
     * the results of this method after all currently available touch data
     * has been provided to one of these two methods.</p>
     *
     * 下面参数directions的解释跟上一个方法checkTouchSlop()是一样的.
     * @param directions Combination of direction flags, see {@link #DIRECTION_HORIZONTAL},
     *                   {@link #DIRECTION_VERTICAL}, {@link #DIRECTION_ALL}
     * @param pointerId ID of the pointer to slop check as specified by MotionEvent
     * @return true if the slop threshold has been crossed, false otherwise
     */
    public boolean checkTouchSlop(int directions, int pointerId) {
        if (!isPointerDown(pointerId)) {
            return false;
        }

        final boolean checkHorizontal = (directions & DIRECTION_HORIZONTAL) == DIRECTION_HORIZONTAL;
        final boolean checkVertical = (directions & DIRECTION_VERTICAL) == DIRECTION_VERTICAL;

        final float dx = mLastMotionX[pointerId] - mInitialMotionX[pointerId];
        final float dy = mLastMotionY[pointerId] - mInitialMotionY[pointerId];

        if (checkHorizontal && checkVertical) {
        	//如果是同时水平和垂直的运动,就检测X和Y轴坐标的平方的和.(直角三角形,两条直角边的平方的和等于第三边的平方.)
            return dx * dx + dy * dy > mTouchSlop * mTouchSlop;
        } else if (checkHorizontal) {
        	//mTouchSlop是临界值, 如果dx的绝对值大于这个值,表示已超出临界值,返回true,否则返回false.
            return Math.abs(dx) > mTouchSlop;
        } else if (checkVertical) {
        	//同前面一个解释.
            return Math.abs(dy) > mTouchSlop;
        }
        return false;
    }

    /**
     * 检测在当前活动的手势中一开始触摸时的屏幕边缘.如果没有活动的手势,该方法会返回false.
     * 
     * Check if any of the edges specified were initially touched in the currently active gesture.
     * If there is no currently active gesture this method will return false.
     *
     * 参数edges - 所检测的边缘
     *
     * @param edges Edges to check for an initial edge touch. See {@link #EDGE_LEFT},
     *              {@link #EDGE_TOP}, {@link #EDGE_RIGHT}, {@link #EDGE_BOTTOM} and
     *              {@link #EDGE_ALL}
     * @return true if any of the edges specified were initially touched in the current gesture
     */
    public boolean isEdgeTouched(int edges) {
        final int count = mInitialEdgesTouched.length;
        for (int i = 0; i < count; i++) {
            if (isEdgeTouched(edges, i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 用于检测边缘中某个有具体ID值的poniter(点).
     * (如果到这里还没有搞懂pointer和触摸或者和drag之间到底有什么关系,不妨这么
     * 想象,一个触摸屏幕的手势是由一定数量的点去组成的,而每一个这样的点,就是一个pointer.手指一触碰屏幕,肯定会产生至少一个
     * pointer.)
     * 如果当前没有活动的手势,或者给定的pointerId并没有对应的pointer,那么该方法会返回false.否则返回true.
     * 
     * Check if any of the edges specified were initially touched by the pointer with
     * the specified ID. If there is no currently active gesture or if there is no pointer with
     * the given ID currently down this method will return false.
     *
     * 参数edges - 该值是EDGE_LEFT, EDGE_TOP, EDGE_RIGHT, EDGE_RIGHT和EDGE_RIGHT中的其中一个.
     * 			很显然,屏幕只有左上右下这四个边缘.
     * @param edges Edges to check for an initial edge touch. See {@link #EDGE_LEFT},
     *              {@link #EDGE_TOP}, {@link #EDGE_RIGHT}, {@link #EDGE_RIGHT} and
     *              {@link #EDGE_ALL}
     * @return true if any of the edges specified were initially touched in the current gesture
     */
    public boolean isEdgeTouched(int edges, int pointerId) {
        return isPointerDown(pointerId) && (mInitialEdgesTouched[pointerId] & edges) != 0;
    }

    //跟dispatchViewReleased(...)方法是类似的,当pointer消失时回收view. 
    private void releaseViewForPointerUp() {
    	//computeCurrentVelocity()方法是根据当前收集到的pointer去计算速度
        mVelocityTracker.computeCurrentVelocity(1000, mMaxVelocity);
        final float xvel = clampMag(
                VelocityTrackerCompat.getXVelocity(mVelocityTracker, mActivePointerId),
                mMinVelocity, mMaxVelocity);
        final float yvel = clampMag(
                VelocityTrackerCompat.getYVelocity(mVelocityTracker, mActivePointerId),
                mMinVelocity, mMaxVelocity);
        dispatchViewReleased(xvel, yvel);
    }

    //拖曳的方法
    private void dragTo(int left, int top, int dx, int dy) {
        int clampedX = left;
        int clampedY = top;
        final int oldLeft = mCapturedView.getLeft();
        final int oldTop = mCapturedView.getTop();
        if (dx != 0) {
            clampedX = mCallback.clampViewPositionHorizontal(mCapturedView, left, dx);
            //设置水平偏移值
            mCapturedView.offsetLeftAndRight(clampedX - oldLeft);
        }
        if (dy != 0) {
            clampedY = mCallback.clampViewPositionVertical(mCapturedView, top, dy);
            //设置垂直偏移值
            mCapturedView.offsetTopAndBottom(clampedY - oldTop);
        }

        if (dx != 0 || dy != 0) {
            final int clampedDx = clampedX - oldLeft;
            final int clampedDy = clampedY - oldTop;
            //既然拖曳肯定发送位置变更,就回调该接口方法
            mCallback.onViewPositionChanged(mCapturedView, clampedX, clampedY,
                    clampedDx, clampedDy);
        }
    }

    /**
     * 获取当前捕获的view是否在父view的坐标系统的某个点之下.如果不存在捕获的view,该方法返回false.
     * (实则,这个方法可获知,触摸的点,捕获的view以及该view所在的父view是否同时处于一个坐标系统.如果是,
     * 这才是一个有效的触摸拖曳事件.)
     * 
     * Determine if the currently captured view is under the given point in the
     * parent view's coordinate system. If there is no captured view this method
     * will return false.
     *
     * @param x X position to test in the parent's coordinate system - 父view坐标系统的X轴坐标
     * @param y Y position to test in the parent's coordinate system - 父view坐标系统的Y轴坐标
     * @return true if the captured view is under the given point, false otherwise
     */
    public boolean isCapturedViewUnder(int x, int y) {
        return isViewUnder(mCapturedView, x, y);
    }

    /**
     * (该方法在上面被调用,也可以外部调用.)
     * 某个view是否在父view给定的点(所处的坐标系统)之下.意思跟上一个方法isCapturedViewUnder(...)是一样的.
     * 
     * Determine if the supplied view is under the given point in the
     * parent view's coordinate system.
     *
     * @param view Child view of the parent to hit test - 父view中所要测试的子view对象
     * @param x X position to test in the parent's coordinate system - 父view中坐标系统的X轴坐标
     * @param y Y position to test in the parent's coordinate system - 父view中坐标系统的Y轴坐标
     * @return true if the supplied view is under the given point, false otherwise
     */
    public boolean isViewUnder(View view, int x, int y) {
        if (view == null) {
            return false;
        }
        //显然,当X,Y所定位的点同时在子view内时才返回true.
        return x >= view.getLeft() &&
                x < view.getRight() &&
                y >= view.getTop() &&
                y < view.getBottom();
    }

    /**
     * 找出父view坐标系统中指定点所定位的最顶层的子view.
     * (这里需要点空间想象力,除了X,Y轴还存在Z轴,Z轴同时垂直X,Y轴.)父view中最顶层的子view,自然能就是Z轴中队对应
     * 的Z值最大的子view.这种排列方法参考方法{@link Callback#getOrderedChildIndex(int)}.
     * (其实我们无需过多关心这个Z轴,如果在布局文件XML中,我们只需要保证添加Drag事件的子view处于父view的顶层可见即可.)
     * 
     * Find the topmost child under the given point within the parent view's coordinate system.
     * The child order is determined using {@link Callback#getOrderedChildIndex(int)}.
     *
     * @param x X position to test in the parent's coordinate system - 父view坐标系统的X轴坐标
     * @param y Y position to test in the parent's coordinate system - 父view坐标系统的Y轴坐标
     * @return The topmost child view under (x, y) or null if none found. - 如果没找到这个最顶层子view则返回null.
     */
    public View findTopChildUnder(int x, int y) {
        final int childCount = mParentView.getChildCount();
        //应该不难理解,直接从i最大的位置开始遍历,第一个就是最顶层.同时还要保证该point的X,Y值都在该子view之内.
        for (int i = childCount - 1; i >= 0; i--) {
            final View child = mParentView.getChildAt(mCallback.getOrderedChildIndex(i));
            if (x >= child.getLeft() && x < child.getRight() &&
                    y >= child.getTop() && y < child.getBottom()) {
                return child;
            }
        }
        return null;
    }
    
    //获取边缘大小
    private int getEdgesTouched(int x, int y) {
        int result = 0; 
        // 因为子view是处于屏幕内,view的坐标轴原点是在左上角.向下为X轴正方形,向右为Y轴正方向.
        // 因此,左侧和顶部都需要加mEdgeSize,而底部和右侧则是减去mEdgeSize,如果X或者Y轴坐标处于加上或减去mEdgeSize
        // 的范围内,则对result和诸如EDGE_LEFT这样的值进行按位或运算,然后赋值给result再返回.
        // 结合上面整个类,到了这里不禁要想为何这里和前面的一些方法都进行按位运算,好简单的理由呵呵:
        // 因为EDGE_LEFT,EDGE_TOP等这些值本身就是二进制取值的.
        if (x < mParentView.getLeft() + mEdgeSize) result |= EDGE_LEFT;
        if (y < mParentView.getTop() + mEdgeSize) result |= EDGE_TOP;
        if (x > mParentView.getRight() - mEdgeSize) result |= EDGE_RIGHT;
        if (y > mParentView.getBottom() - mEdgeSize) result |= EDGE_BOTTOM;

        return result;
    }
}