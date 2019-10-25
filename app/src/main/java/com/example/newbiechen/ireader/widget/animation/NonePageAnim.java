package com.example.newbiechen.ireader.widget.animation;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.example.newbiechen.ireader.ui.activity.ReadActivity;
import com.example.newbiechen.ireader.widget.page.PageView;

/**
 * Created by newbiechen on 17-7-24.
 */

public class NonePageAnim extends HorizonPageAnim {
    public static final String TAG = "NonePageAnim";
    // 绘制和显示区域
    private Rect mSrcRect, mDestRect;
    // 处理阴影
    private GradientDrawable mBackShadowDrawableLR;
    public boolean isOpenAuto;
    public boolean isCanHandleMessage = true;
    private MyThread thread;
    /**
     * 首次开启自动阅读   【防止首次 启动时  获取不到第一页   待解决  首次自动阅读  nextBitmap绘制下一页 再执行】
     */
    private boolean isFirstAuto = true;

    private boolean isMove;
    private int mMoveX;
    private int mMoveY;

    private int touchRectBottom;

    //移动的点击位置

    public NonePageAnim(int w, int h, View view, OnPageChangeListener listener) {
        super(w, h, view, listener);
        // 初始化自动阅读的绘制区域
        mSrcRect = new Rect(0, 0, mViewWidth, 0);
        mDestRect = new Rect(0, 0, mViewWidth, 0);
        // 自动阅读的阴影条
        int[] mBackShadowColors = new int[]{0x66000000, 0x00000000};
        mBackShadowDrawableLR = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, mBackShadowColors);
        mBackShadowDrawableLR.setGradientType(GradientDrawable.LINEAR_GRADIENT);
    }

    private void resetRect() {
        mSrcRect = new Rect(0, 0, mViewWidth, 0);
        mDestRect = new Rect(0, 0, mViewWidth, 0);
    }

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (!isCanHandleMessage) {
                return;
            }
            Log.e(TAG, "---------------mHandler" + mDestRect.bottom + "---" + mDestRect.height() + "---mViewHeight:" + mViewHeight + "--screen:" + mScreenHeight);
            switch (msg.what) {
                case 1:
                    if (mDestRect.height() > mViewHeight) {
                        //判断是否下一页存在  绘制下一页
                        resetRect();
                        boolean hasNext = mListener.hasNext();
                        isFirstAuto = false;
                        Log.e(TAG, "---------------超了屏幕" + mDestRect.bottom + "--hasNext:" + hasNext + "----isMain:" + ReadActivity.isMainThread());
                        if (hasNext) {
                            //设置动画方向
                            mView.invalidate();
                        }
                    } else {
                        mDestRect.bottom += 10;
                        mSrcRect.bottom += 10;
                        if (mView != null) {
                            mView.invalidate();
                        }
//                        Log.e(TAG, "---------------一平" + mDestRect.bottom + "-----是否是主线程：" + ReadActivity.isMainThread());
                    }
                    break;
                default:
                    break;
            }
        }
    };

    public class MyThread extends Thread {//继承Thread类

        @Override
        public void run() {
            while (isOpenAuto) {
                try {
                    Thread.sleep(50);
                    Message message = Message.obtain();
                    message.what = 1;
                    mHandler.sendMessage(message);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isOpenAuto) {
            // 没有开启自动阅读
            Log.e(TAG, "-------------------没开启自动阅读");
            return super.onTouchEvent(event);
        }
        //获取点击位置
        int x = (int) event.getX();
        int y = (int) event.getY();
        //设置触摸点
        setTouchPoint(x, y);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 按下   暂停阅读
                Log.e(TAG, "-------------------自动阅读中被点击了");

                setStartPoint(x, y);
                mMoveX = 0;
                mMoveY = 0;
                touchRectBottom = mSrcRect.bottom;
                isCanHandleMessage = false;
                break;
            case MotionEvent.ACTION_MOVE:
                // 随着手指移动
                final int slop = ViewConfiguration.get(mView.getContext()).getScaledTouchSlop();
                //判断是否移动了
                if (!isMove) {
                    isMove = Math.abs(mStartX - x) > slop || Math.abs(mStartY - y) > slop;
                }

                if (isMove) {
                    mMoveX = x;
                    mMoveY = y;
                    isRunning = true;
                    mView.invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
                // 非移动   弹出窗口
                if (!isMove) {
                    // 弹层
                } else {
                    // 移动了 不处理
                    isCanHandleMessage = true;
                    isRunning = false;
                }
                break;
            default:
                break;
        }
        Log.e(TAG, "-------------------开启自动阅读  执行最后");
        return true;
    }

    public void autoRead() {
        isOpenAuto = true;
        if (thread == null) {
            thread = new MyThread();
            thread.start();
            isFirstAuto = true;
            Log.e(TAG, "---------------autoRead");
        }
    }

    public void cancelAuto() {
        if (thread != null) {
            isOpenAuto = false;
            isCanHandleMessage = false;
            thread.interrupt();
            thread = null;
        }
        if (mHandler != null) {
            Log.e(TAG, "---------------退出接受消息了");
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void drawStatic(Canvas canvas) {
        if (isOpenAuto) {
            canvas.setDrawFilter(new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            if (!isFirstAuto) {
                canvas.drawBitmap(mCurBitmap, 0, 0, null);
            }
            canvas.drawBitmap(mNextBitmap, mSrcRect, mDestRect, null);
            addShadow(mSrcRect.bottom, canvas);
        } else {
            if (isCancel) {
                Log.e(TAG, "---------------drawStatic  mCurBitmap");
                canvas.drawBitmap(mCurBitmap, 0, 0, null);
            } else {
                Log.e(TAG, "---------------drawStatic  mNextBitmap");
                canvas.drawBitmap(mNextBitmap, 0, 0, null);
                mCurBitmap = mNextBitmap.copy(Bitmap.Config.RGB_565, true);
            }
        }
    }

    //添加阴影
    public void addShadow(int left, Canvas canvas) {
        mBackShadowDrawableLR.setBounds(0, left, mScreenWidth, left + 30);
        mBackShadowDrawableLR.draw(canvas);
    }

    @Override
    public void drawMove(Canvas canvas) {
        if (isMove) {
            Log.e(TAG, "---------------drawMove");
            // 随着手指滑动
            int dis = (int) (mMoveY - mStartY);
            if ((touchRectBottom + dis) > mViewHeight) {
                mDestRect.bottom = mViewHeight;
            } else if ((touchRectBottom + dis) < 0) {
                mDestRect.bottom = 0;
            }
            Log.e(TAG, "--------------------- mDestRect.bottom:" + mDestRect.bottom + "---dis:" + dis);
            //计算bitmap截取的区域
            mSrcRect.top = 0;
            mDestRect.top = 0;
            mSrcRect.bottom = touchRectBottom + dis;
            //计算bitmap在canvas显示的区域
            mDestRect.bottom = touchRectBottom + dis;
            canvas.setDrawFilter(new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            if (!isFirstAuto) {
                canvas.drawBitmap(mCurBitmap, 0, 0, null);
            }
            canvas.drawBitmap(mNextBitmap, mSrcRect, mDestRect, null);
            addShadow(mSrcRect.bottom, canvas);
        }
    }

    @Override
    public void startAnim() {
    }
}
