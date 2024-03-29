package com.example.newbiechen.ireader.widget.page;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.example.newbiechen.ireader.model.bean.CollBookBean;
import com.example.newbiechen.ireader.widget.animation.CoverPageAnim;
import com.example.newbiechen.ireader.widget.animation.HorizonPageAnim;
import com.example.newbiechen.ireader.widget.animation.NonePageAnim;
import com.example.newbiechen.ireader.widget.animation.PageAnimation;
import com.example.newbiechen.ireader.widget.animation.ScrollPageAnim;
import com.example.newbiechen.ireader.widget.animation.SimulationPageAnim;
import com.example.newbiechen.ireader.widget.animation.SlidePageAnim;

/**
 * Created by Administrator on 2016/8/29 0029.
 * 原作者的GitHub Project Path:(https://github.com/PeachBlossom/treader)
 * 绘制页面显示内容的类
 */
public class PageView extends View {


    private final static String TAG = "BookPageWidget";
    /**
     * 当前View的宽
     */
    private int mViewWidth = 0;
    /**
     * 当前View的高
     */
    private int mViewHeight = 0;
    /**
     * 手指按下坐标  判断是否移动
     */
    private int mStartX = 0;
    private int mStartY = 0;
    /**
     * 是否手指滑动
     */
    private boolean isMove = false;
    // 初始化参数
    private int mBgColor = 0xFFCEC29C;
    private PageMode mPageMode = PageMode.SIMULATION;
    // 是否允许点击
    private boolean canTouch = true;
    // 唤醒菜单的区域
    private RectF mCenterRect = null;
    /**
     * pageView是否初始化完成
     */
    private boolean isViewPrepare;
    /**
     * 动画类
     */
    private PageAnimation mPageAnim;
    // 动画监听类
    private PageAnimation.OnPageChangeListener mPageAnimListener = new PageAnimation.OnPageChangeListener() {
        /**
         *  从数据源开始判断是否含有上一页  并切换到上一页 进行绘制
         * @return
         */
        @Override
        public boolean hasPrev() {
            return PageView.this.hasPrevPage();
        }

        /**
         *  翻到下一页
         * @return
         */
        @Override
        public boolean hasNext() {
            return PageView.this.hasNextPage();
        }

        /**
         *  此方法可以取消翻页
         */
        @Override
        public void pageCancel() {
            PageView.this.pageCancel();
        }
    };

    //点击监听
    private TouchListener mTouchListener;
    //内容加载器
    private PageLoader mPageLoader;

    public PageView(Context context) {
        this(context, null);
    }

    public PageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mViewWidth = w;
        mViewHeight = h;

        isViewPrepare = true;

        if (mPageLoader != null) {
            mPageLoader.prepareDisplay(w, h);
        }
    }

    /**
     * 设置翻页的模式
     */
    void setPageMode(PageMode pageMode) {
        mPageMode = pageMode;
        //视图未初始化的时候，禁止调用
        if (mViewWidth == 0 || mViewHeight == 0) return;

        switch (mPageMode) {
            case SIMULATION:
                mPageAnim = new SimulationPageAnim(mViewWidth, mViewHeight, this, mPageAnimListener);
                break;
            case COVER:
                mPageAnim = new CoverPageAnim(mViewWidth, mViewHeight, this, mPageAnimListener);
                break;
            case SLIDE:
                mPageAnim = new SlidePageAnim(mViewWidth, mViewHeight, this, mPageAnimListener);
                break;
            case NONE:
                mPageAnim = new NonePageAnim(mViewWidth, mViewHeight, this, mPageAnimListener);
                break;
            case SCROLL:
                mPageAnim = new ScrollPageAnim(mViewWidth, mViewHeight, 0, mPageLoader.getMarginHeight(), this, mPageAnimListener);
                break;
            default:
                mPageAnim = new SimulationPageAnim(mViewWidth, mViewHeight, this, mPageAnimListener);
        }
    }

    public void setAutoRead(boolean open) {
        if (mPageAnim instanceof NonePageAnim) {
            if (open) {
                ((NonePageAnim) mPageAnim).autoRead();
            } else {
                ((NonePageAnim) mPageAnim).cancelAuto();
            }
        }
    }

    /**
     * 绘制时获取下一个展示内容的  空闲 的bitmap
     *
     * @return
     */
    public Bitmap getNextBitmap() {
        if (mPageAnim == null) return null;
        return mPageAnim.getNextBitmap();
    }

    /**
     * 获取背景bitmap
     *
     * @return
     */
    public Bitmap getBgBitmap() {
        if (mPageAnim == null) return null;
        return mPageAnim.getBgBitmap();
    }

    /**
     * 音量键翻页用  翻到前一页
     *
     * @return
     */
    public boolean autoPrevPage() {
        //滚动暂时不支持自动翻页
        if (mPageAnim instanceof ScrollPageAnim) {
            return false;
        } else {
            startPageAnim(PageAnimation.Direction.PRE);
            return true;
        }
    }

    /**
     * 音量键翻页用   翻到后一页
     *
     * @return
     */
    public boolean autoNextPage() {
        if (mPageAnim instanceof ScrollPageAnim) {
            return false;
        } else {
            startPageAnim(PageAnimation.Direction.NEXT);
            return true;
        }
    }

    /**
     * 设置除 上下滑动外    左右翻页初始点  并开始翻页
     *
     * @param direction
     */
    private void startPageAnim(PageAnimation.Direction direction) {
        if (mTouchListener == null) return;
        //是否正在执行动画
        abortAnimation();
        if (direction == PageAnimation.Direction.NEXT) {
            int x = mViewWidth;
            int y = mViewHeight;
            //初始化动画
            mPageAnim.setStartPoint(x, y);
            //设置点击点
            mPageAnim.setTouchPoint(x, y);
            //设置方向
            Boolean hasNext = hasNextPage();

            mPageAnim.setDirection(direction);
            if (!hasNext) {
                return;
            }
        } else {
            int x = 0;
            int y = mViewHeight;
            //初始化动画
            mPageAnim.setStartPoint(x, y);
            //设置点击点
            mPageAnim.setTouchPoint(x, y);
            mPageAnim.setDirection(direction);
            //设置方向方向
            Boolean hashPrev = hasPrevPage();
            if (!hashPrev) {
                return;
            }
        }
        mPageAnim.startAnim();
        this.postInvalidate();
    }

    /**
     * 设置阅读背景色
     *
     * @param color
     */
    public void setBgColor(int color) {
        mBgColor = color;
    }

    @Override
    protected void onDraw(Canvas canvas) {
//        Log.e("PageView", "-----------------onDraw");

        //绘制背景
        canvas.drawColor(mBgColor);

        //绘制动画
        mPageAnim.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);

        if (!canTouch && event.getAction() != MotionEvent.ACTION_DOWN) return true;

        int x = (int) event.getX();
        int y = (int) event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mPageAnim instanceof  NonePageAnim &&((NonePageAnim) mPageAnim).isOpenAuto) {
                    mPageAnim.onTouchEvent(event);
                    return true;
                }

                mStartX = x;
                mStartY = y;
                isMove = false;
                canTouch = mTouchListener.onTouch();

                break;
            case MotionEvent.ACTION_MOVE:
                // 判断是否大于最小滑动值。
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (!isMove) {
                    isMove = Math.abs(mStartX - event.getX()) > slop || Math.abs(mStartY - event.getY()) > slop;
                }

                // 如果滑动了，则进行翻页。    无翻页情况 滑动不处理
                if (isMove && (mPageAnim instanceof NonePageAnim && ((NonePageAnim) mPageAnim).isOpenAuto)) {
                    mPageAnim.onTouchEvent(event);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!isMove) {
                    //设置中间区域范围
                    if (mCenterRect == null) {
                        mCenterRect = new RectF(mViewWidth / 5, mViewHeight / 3,
                                mViewWidth * 4 / 5, mViewHeight * 2 / 3);
                    }

                    //是否点击了中间
                    if (mCenterRect.contains(x, y)) {
                        if (mTouchListener != null) {
                            mTouchListener.center();
                        }
                        return true;
                    }
                }
                if(isMove && (mPageAnim instanceof NonePageAnim && !((NonePageAnim) mPageAnim).isOpenAuto)){
                    // 无动画情况下  未开自动阅读  不处理
                } else {
                    mPageAnim.onTouchEvent(event);
                }
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * 判断数据源    是否存在上一页  存在的话直接切换 并绘制
     *
     * @return
     */
    private boolean hasPrevPage() {
        mTouchListener.prePage();
        return mPageLoader.prev();
    }

    /**
     * 判断数据源    是否下一页存在
     *
     * @return
     */
    private boolean hasNextPage() {
        Log.e("auto", "--------------------hasNextPage()");
        mTouchListener.nextPage();
        return mPageLoader.next();
    }

    /**
     * 取消翻页
     */
    private void pageCancel() {
        mTouchListener.cancel();
        mPageLoader.pageCancel();
    }

    /**
     * 滑动计算   在onDraw中 每次绘制调用   而滑动 实际调用的是ScrollTo  等方法
     * ScrollTo 调用完毕后会 调用刷新方法   如此循环调用 需要在判断
     */
    @Override
    public void computeScroll() {
//        Log.e("ScrollAnimation", "-----------------computeScroll");
        //进行滑动
        mPageAnim.scrollAnim();
        super.computeScroll();
    }

    /**
     * 如果滑动状态没有停止就取消状态，重新设置Anim的触碰点
     */
    public void abortAnimation() {
        mPageAnim.abortAnim();
    }

    /**
     * 动画是否在执行
     *
     * @return
     */
    public boolean isRunning() {
        if (mPageAnim == null) {
            return false;
        }
        return mPageAnim.isRunning();
    }

    /**
     * pageview 是否初始化完成
     * @return
     */
    public boolean isPageViewPrepare() {
        return isViewPrepare;
    }

    /**
     * 设置触摸事件
     *
     * @param mTouchListener
     */
    public void setTouchListener(TouchListener mTouchListener) {
        this.mTouchListener = mTouchListener;
    }

    /**
     * 调用绘制方法前  下一页数据源已经赋值给mCurPage
     */
    public void drawNextPage() {
        Log.e(TAG, "-----------------drawNextPage 绘制下一页bitmap");
        if (!isViewPrepare) return;

        if (mPageAnim instanceof HorizonPageAnim) {
            ((HorizonPageAnim) mPageAnim).changePage();
        }
        mPageLoader.drawPage(getNextBitmap(), false);
    }

    /**
     * 绘制当前页。
     *
     * @param isUpdateBatteryOrTime 是否是更新电池 和 时间   true  只刷新电量和  时间  false  刷新全部
     */
    public void drawCurPage(boolean isUpdateBatteryOrTime) {
        Log.e(TAG, "-----------------drawCurPage 绘制当前页bitmap");
        // pageview  没有初始化完成
        if (!isViewPrepare) return;

        if (!isUpdateBatteryOrTime) {
            if (mPageAnim instanceof ScrollPageAnim) {
                ((ScrollPageAnim) mPageAnim).resetBitmap();
            }
        }
        mPageLoader.drawPage(getNextBitmap(), isUpdateBatteryOrTime);
    }

    /**
     * 释放资源
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mPageAnim.abortAnim();
        mPageAnim.clear();

        mPageLoader = null;
        mPageAnim = null;
    }

    /**
     * 获取 PageLoader  内容加载器  【网络和本地】
     *
     * @param collBook
     * @return
     */
    public PageLoader getPageLoader(CollBookBean collBook) {
        // 判是否已经存在
        if (mPageLoader != null) {
            return mPageLoader;
        }
        // 根据书籍类型，获取具体的加载器
        if (collBook.isLocal()) {
            mPageLoader = new LocalPageLoader(this, collBook);
        } else {
            mPageLoader = new NetPageLoader(this, collBook);
        }
        // 判断是否 PageView 已经初始化完成
        if (mViewWidth != 0 || mViewHeight != 0) {
            // 初始化 PageLoader 的屏幕大小
            mPageLoader.prepareDisplay(mViewWidth, mViewHeight);
        }

        return mPageLoader;
    }

    /**
     * 设置触摸监听的回调
     */
    public interface TouchListener {
        boolean onTouch();

        void center();

        void prePage();

        void nextPage();

        void cancel();
    }
}
