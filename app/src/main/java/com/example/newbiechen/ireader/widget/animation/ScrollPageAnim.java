package com.example.newbiechen.ireader.widget.animation;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by newbiechen on 17-7-23.
 * 原理:仿照ListView源码实现的上下滑动效果
 * Alter by: zeroAngus
 * <p>
 * 问题:
 * 1. 向上翻页，重复的问题 (完成)
 * 2. 滑动卡顿的问题。原因:由于绘制的数据过多造成的卡顿问题。 (主要是文字绘制需要的时长比较多) 解决办法：做文字缓冲
 * 3. 弱网环境下，显示的问题
 */
public class ScrollPageAnim extends PageAnimation {
    private static final String TAG = "ScrollAnimation";
    // 滑动追踪的时间
    private static final int VELOCITY_DURATION = 1000;
    /**
     * 速度追踪器   结合  Scroller  实现 惯性滑动
     */
    private VelocityTracker mVelocity;

    /**
     * 整个Bitmap的背景显示
     */
    private Bitmap mBgBitmap;

    /**
     * 下一个展示的图片
     */
    private Bitmap mNextBitmap;

    /**
     *  缓存未用的位图集合
     */
    private ArrayDeque<BitmapView> mScrapViews;
    /**
     * 正在被利用的图片列表  【当前界面占用的位图集合】
     */
    private ArrayList<BitmapView> mActiveViews = new ArrayList<>(2);

    // 是否处于刷新阶段
    private boolean isRefresh = true;
    // 底部填充
    private Iterator<BitmapView> downIt;
    private Iterator<BitmapView> upIt;

    /**
     * 滑动构造方法
     *
     * @param w
     * @param h
     * @param marginWidth
     * @param marginHeight
     * @param view         pageview
     * @param listener     翻页监听
     */
    public ScrollPageAnim(int w, int h, int marginWidth, int marginHeight,
                          View view, OnPageChangeListener listener) {
        super(w, h, marginWidth, marginHeight, view, listener);
        // 创建两个BitmapView
        initWidget();
    }

    /**
     * 初始化控件
     */
    private void initWidget() {
        mBgBitmap = Bitmap.createBitmap(mScreenWidth, mScreenHeight, Bitmap.Config.RGB_565);

        // 新建 位图view  缓存 到备用列表
        mScrapViews = new ArrayDeque<>(2);
        for (int i = 0; i < 2; ++i) {
            BitmapView view = new BitmapView();
            view.bitmap = Bitmap.createBitmap(mViewWidth, mViewHeight, Bitmap.Config.RGB_565);
            view.srcRect = new Rect(0, 0, mViewWidth, mViewHeight);
            view.destRect = new Rect(0, 0, mViewWidth, mViewHeight);
            view.top = 0;
            view.bottom = view.bitmap.getHeight();
            mScrapViews.push(view);
        }
        onLayout();
        isRefresh = false;
    }

    /**
     * 修改布局,填充内容
     */
    private void onLayout() {
        // 如果还没有开始加载，则从上到下进行绘制
        if (mActiveViews.size() == 0) {
            // 首次加载  从下往上填充
            fillDown(0, 0);
            mDirection = Direction.NONE;
        } else {
            // 已经加载过    判断是下滑还是上拉
            int offset = (int) (mTouchY - mLastY);
            // 【偏移量为正   下拉】
            if (offset > 0) {
                int topEdge = mActiveViews.get(0).top;
                // 从上向下填充
                fillUp(topEdge, offset);
            }
            // 【负的   上滑】
            else {
                // 底部的距离 = 当前底部的距离 + 滑动的距离 (因为上滑，得到的值肯定是负的)
                int bottomEdge = mActiveViews.get(mActiveViews.size() - 1).bottom;
                fillDown(bottomEdge, offset);
            }
        }
    }

    /**
     * 创建View填充底部空白部分   【上滑  和  首次进入   填充下方】
     *
     * @param bottomEdge :当前最后一个View的底部，在整个屏幕上的位置,即相对于屏幕顶部的距离 【未滑动前的最后一个view  距离顶部的距离】
     * @param offset     :滑动的偏移量   【1 首次偏移量为0   2  上滑 偏移量为负的  即 位图向上走】
     */
    private void fillDown(int bottomEdge, int offset) {
        // 获取当前显示bitmap列表迭代器
        downIt = mActiveViews.iterator();
        BitmapView view;

        // 轮询当前展示列表  每个条目  按照手势移动的偏移后  重置位置  并且判断上滑的是否滑出了屏幕
        while (downIt.hasNext()) {
            view = downIt.next();
            // 当前展示的位图  展示范围整体上移offset 偏移量
            view.top = view.top + offset;
            view.bottom = view.bottom + offset;
            // 设置允许显示的范围
            view.destRect.top = view.top;
            view.destRect.bottom = view.bottom;

            // 移动后   判断当前展示用的  bitmapView是否移除了屏幕
            if (view.bottom <= 0) {
                // 划出屏幕了   添加到备用列表中
                mScrapViews.add(view);
                // 从当前使用列表中移除
                downIt.remove();
                // 如果原先是从上加载，现在变成从下加载，则表示取消
                if (mDirection == Direction.UP) {
                    mListener.pageCancel();
                    mDirection = Direction.NONE;
                }
            }
        }

        // 滑动之后的最后一个 View 的距离屏幕顶部上的实际位置 = 滑动前的底部位置 + 偏移量
        int realEdge = bottomEdge + offset;

        /**
         *  判断是否需要底部插入
         */
        // 滑动完后  最后一个展示的bitmapview  可见部分 小于屏幕  并且  当前就一个展示的view   从备用取出来填充
        while (realEdge < mViewHeight && mActiveViews.size() < 2) {
            // 从废弃的Views中获取一个
            view = mScrapViews.getFirst();
/*          //擦除其Bitmap(重新创建会不会更好一点)
            eraseBitmap(view.bitmap,view.bitmap.getWidth(),view.bitmap.getHeight(),0,0);*/
            if (view == null) return;

            // 存下一页bitmap数据   用于  没有下一页  恢复数据
            Bitmap cancelBitmap = mNextBitmap;
            // 下一个  为 取出来的空闲的bitmap  新的数据
            mNextBitmap = view.bitmap;

            if (!isRefresh) {
                // 上滑 是否还有下一页
                //如果不成功则无法滑动
                boolean hasNext = mListener.hasNext();
                // 如果不存在next,则进行还原
                if (!hasNext) {
                    // 下一个  恢复以前状态
                    mNextBitmap = cancelBitmap;
                    // 重置当前展示bitmapView  展示范围为全屏
                    for (BitmapView activeView : mActiveViews) {
                        activeView.top = 0;
                        activeView.bottom = mViewHeight;
                        // 设置允许显示的范围
                        activeView.destRect.top = activeView.top;
                        activeView.destRect.bottom = activeView.bottom;
                    }
                    abortAnim();
                    return;
                }
            }

            // 如果加载成功 有下一页  ，那么就将View从ScrapViews中移除
            mScrapViews.removeFirst();
            // 添加到展示列表的底部
            mActiveViews.add(view);
            // 方向改为从底下填充
            mDirection = Direction.DOWN;

            // 设置Bitmap的范围   第二个展示的bitmapView  top:延续上一个的底部   底部到屏幕顶部的距离为  top+bitmap高度
            view.top = realEdge;
            view.bottom = realEdge + view.bitmap.getHeight();
            // 设置允许显示的范围
            view.destRect.top = view.top;
            view.destRect.bottom = view.bottom;
            // 改变判断条件
            realEdge += view.bitmap.getHeight();
        }
    }

    /**
     * 创建View填充顶部空白部分   【下拉  填充上方】
     *
     * @param topEdge : 当前第一个View的顶部，到屏幕顶部的距离
     * @param offset  : 滑动的偏移量   【下拉  偏移量 全为正数】
     */
    private void fillUp(int topEdge, int offset) {
        // 首先进行布局的调整
        upIt = mActiveViews.iterator();
        BitmapView view;
        // 轮询当前显示列表   整体bitmap的展示范围整体下移 offset   【绘制时  绘制下移效果】
        while (upIt.hasNext()) {
            view = upIt.next();
            view.top = view.top + offset;
            view.bottom = view.bottom + offset;
            //设置允许显示的范围
            view.destRect.top = view.top;
            view.destRect.bottom = view.bottom;

            // 整体下移后  判断下移后  移出屏幕的  【顶部距离 大于  屏幕高度即为 移出】
            if (view.top >= mViewHeight) {
                // 添加到备用的View中
                mScrapViews.add(view);
                // 从Active中移除
                upIt.remove();

                // 如果原先是下，现在变成从上加载了，则表示取消加载

                if (mDirection == Direction.DOWN) {
                    mListener.pageCancel();
                    mDirection = Direction.NONE;
                }
            }
        }

        // 滑动之后，第一个 View 的顶部距离屏幕顶部的实际位置 = 第一个顶部  +offset 。
        int realEdge = topEdge + offset;

        /**
         *  判断是否需要从上插入  备用view
         */
        // 偏移后  第一个不是全屏展示  向下移动   && 当前展示用的bitmapView 小于2个   则需要从上插入一个备用bitmapview
        while (realEdge > 0 && mActiveViews.size() < 2) {
            // 从废弃的Views中获取一个
            view = mScrapViews.getFirst();
            if (view == null) return;

            // 缓存备用 bitmapview数据
            Bitmap cancelBitmap = mNextBitmap;
            mNextBitmap = view.bitmap;
            if (!isRefresh) {
                // 如果不成功则无法滑动
                boolean hasPrev = mListener.hasPrev();
                // 如果不存在上一个,则进行还原
                if (!hasPrev) {
                    mNextBitmap = cancelBitmap;
                    // 其实就一个展示的view   没有上一个 就回复全屏显示
                    for (BitmapView activeView : mActiveViews) {
                        activeView.top = 0;
                        activeView.bottom = mViewHeight;
                        // 设置允许显示的范围
                        activeView.destRect.top = activeView.top;
                        activeView.destRect.bottom = activeView.bottom;
                    }
                    abortAnim();
                    return;
                }
            }
            // 如果加载成功，那么就将View从ScrapViews中移除
            mScrapViews.removeFirst();
            // 从上   加入到存活的对象中
            mActiveViews.add(0, view);
            mDirection = Direction.UP;
            // 设置Bitmap的范围
            view.top = realEdge - view.bitmap.getHeight();
            // 新bitmapView   可显示范围的底部  到  屏幕  顶部的距离 为  上方剩余高度
            view.bottom = realEdge;

            // 设置允许显示的范围
            view.destRect.top = view.top;
            view.destRect.bottom = view.bottom;
            realEdge -= view.bitmap.getHeight();
        }
    }

    /**
     * 对Bitmap进行擦除
     *
     * @param b
     * @param width
     * @param height
     * @param paddingLeft
     * @param paddingTop
     */
    private void eraseBitmap(Bitmap b, int width, int height,
                             int paddingLeft, int paddingTop) {
     /*   if (mInitBitmapPix == null) return;
        b.setPixels(mInitBitmapPix, 0, width, paddingLeft, paddingTop, width, height);*/
    }

    /**
     * 重置翻页方式   重新布局
     */
    public void resetBitmap() {
        isRefresh = true;
        // 将所有的Active加入到Scrap中
        for (BitmapView view : mActiveViews) {
            mScrapViews.add(view);
        }
        // 清除所有的Active
        mActiveViews.clear();
        // 重新进行布局
        onLayout();
        isRefresh = false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();

        // 初始化速度追踪器
        if (mVelocity == null) {
            mVelocity = VelocityTracker.obtain();
        }
        // 事件传递给 VelocityTracker  这样才能使得 VelocityTracker 调用 computeCurrentVelocity 正确取得当前速度
        mVelocity.addMovement(event);
        // 设置触碰点
        setTouchPoint(x, y);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isRunning = false;
                // 设置起始点
                setStartPoint(x, y);
                // 停止动画
                abortAnim();
                break;
            case MotionEvent.ACTION_MOVE:
                mVelocity.computeCurrentVelocity(VELOCITY_DURATION);
                isRunning = true;
                // 随着手指 移动 不断重置  偏移量  不断进行刷新  调用绘制里的Onlayout  进行移动
                mView.postInvalidate();
                break;
            case MotionEvent.ACTION_UP:
                Log.e(TAG, "MotionEvent.ACTION_UP");
                isRunning = false;
                // 开启惯性滑动动画
                startAnim();
                // 删除检测器
                mVelocity.recycle();
                mVelocity = null;
                break;

            case MotionEvent.ACTION_CANCEL:
                try {
                    mVelocity.recycle(); // if velocityTracker won't be used should be recycled
                    mVelocity = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            default:
                break;
        }
        return true;
    }


    BitmapView tmpView;

    /**
     * 执行绘制
     *
     * @param canvas
     */
    @Override
    public void draw(Canvas canvas) {
        Log.e(TAG, "draw");
        //进行布局
        onLayout();

        //绘制背景
        canvas.drawBitmap(mBgBitmap, 0, 0, null);
        //绘制内容
        canvas.save();
        //移动位置
        canvas.translate(0, mMarginHeight);
        //裁剪显示区域
        canvas.clipRect(0, 0, mViewWidth, mViewHeight);
/*        //设置背景透明
        canvas.drawColor(0x40);*/
        //绘制Bitmap
        for (int i = 0; i < mActiveViews.size(); ++i) {
            tmpView = mActiveViews.get(i);
            canvas.drawBitmap(tmpView.bitmap, tmpView.srcRect, tmpView.destRect, null);
        }
        canvas.restore();
    }

    /**
     * 松手后开始执行惯性动画   从起始到终止位置
     * 【松手执行计算偏移方法借助onMove中 mView.postInvalidate(); ->onDraw ->computeScroll滑动计算->scrollAnim 滑动未完成继续绘制偏移】
     */
    @Override
    public synchronized void startAnim() {
        Log.e(TAG, "startAnim");
        isRunning = true;
        mScroller.fling(0, (int) mTouchY, 0, (int) mVelocity.getYVelocity()
                , 0, 0, Integer.MAX_VALUE * -1, Integer.MAX_VALUE);
    }

    /**
     * 通过判断滑动是否完成 来绘制界面
     */
    @Override
    public void scrollAnim() {
        Log.e(TAG, "scrollAnim");
        // 滑动是否结束
        if (mScroller.computeScrollOffset()) {
            // 滑动未结束   设置新的偏移   刷新界面
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();
            setTouchPoint(x, y);
            if (mScroller.getFinalX() == x && mScroller.getFinalY() == y) {
                isRunning = false;
            }
            mView.postInvalidate();
        }
    }

    /**
     * 停止动画
     */
    @Override
    public void abortAnim() {
        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
            isRunning = false;
        }
    }

    @Override
    public Bitmap getBgBitmap() {
        return mBgBitmap;
    }

    @Override
    public Bitmap getNextBitmap() {
        return mNextBitmap;
    }

    /**
     * 封装要绘制bitmap 信息
     */
    private static class BitmapView {
        /**
         * 要绘制的bitmap
         */
        Bitmap bitmap;
        /**
         * 定义绘制bitmap的那一部分  区域  【对bitmap 进行裁剪】
         */
        Rect srcRect;
        /**
         * 裁剪后bitmap 绘画的位置，就是你想把这张裁剪好的图片放在屏幕的什么位置上
         * srcRect  比 destRect 大     bitmap等比例缩小
         * srcRect  比 destRect 小     bitmap等比例放大
         */
        Rect destRect;
        int top;
        int bottom;
    }
}
