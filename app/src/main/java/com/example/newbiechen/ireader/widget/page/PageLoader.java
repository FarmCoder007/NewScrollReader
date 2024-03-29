package com.example.newbiechen.ireader.widget.page;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.text.TextPaint;
import android.util.Log;

import com.example.newbiechen.ireader.model.bean.BookRecordBean;
import com.example.newbiechen.ireader.model.bean.CollBookBean;
import com.example.newbiechen.ireader.model.local.BookRepository;
import com.example.newbiechen.ireader.model.local.ReadSettingManager;
import com.example.newbiechen.ireader.utils.Constant;
import com.example.newbiechen.ireader.utils.IOUtils;
import com.example.newbiechen.ireader.utils.RxUtils;
import com.example.newbiechen.ireader.utils.ScreenUtils;
import com.example.newbiechen.ireader.utils.StringUtils;
import com.example.newbiechen.ireader.widget.animation.HorizonPageAnim;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleObserver;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.disposables.Disposable;

/**
 * Created by newbiechen on 17-7-1.
 */

public abstract class PageLoader {
    private static final String TAG = "PageLoader";

    // 当前页面的状态
    public static final int STATUS_LOADING = 1;         // 正在加载
    public static final int STATUS_FINISH = 2;          // 加载完成
    public static final int STATUS_ERROR = 3;           // 加载错误 (一般是网络加载情况)
    public static final int STATUS_EMPTY = 4;           // 空数据
    public static final int STATUS_PARING = 5;          // 正在解析 (装载本地数据)
    public static final int STATUS_PARSE_ERROR = 6;     // 本地文件解析错误(暂未被使用)
    public static final int STATUS_CATEGORY_EMPTY = 7;  // 获取到的目录为空
    // 默认的显示参数配置
    private static final int DEFAULT_MARGIN_HEIGHT = 28;
    private static final int DEFAULT_MARGIN_WIDTH = 15;
    private static final int DEFAULT_TIP_SIZE = 12;
    private static final int EXTRA_TITLE_SIZE = 4;

    /**
     * 当前书籍章节列表
     */
    protected List<TxtChapter> mChapterList;
    /**
     * 书本对象
     */
    protected CollBookBean mCollBook;
    /**
     * page 变化 监听器
     */
    protected OnPageChangeListener mPageChangeListener;

    private Context mContext;
    // 页面显示类
    private PageView mPageView;
    /**
     * 当前显示绘制页面的 数据源
     */
    private TxtPage mCurPage;
    /**
     * 上一章的页面数据源列表    排版后的
     */
    private List<TxtPage> mPrePageList;
    /**
     * 当前章节的页面列表
     */
    private List<TxtPage> mCurPageTxtList;
    /**
     * 下一章的页面列表缓存
     */
    private List<TxtPage> mNextPageTxtList;

    // 绘制电池的画笔
    private Paint mBatteryPaint;
    // 绘制提示的画笔
    private Paint mTipPaint;
    // 绘制标题的画笔
    private Paint mTitlePaint;
    // 绘制背景颜色的画笔(用来擦除需要重绘的部分)
    private Paint mBgPaint;
    // 绘制小说内容的画笔
    private TextPaint mTextPaint;
    // 阅读器的配置选项
    private ReadSettingManager mSettingManager;
    // 被遮盖的页，或者认为被取消显示的页
    private TxtPage mCancelPage;
    /**
     * 书签类
     */
    private BookRecordBean mBookRecord;

    private Disposable mPreLoadDisp;

    /*****************params**************************/
    // 当前的状态
    protected int mStatus = STATUS_LOADING;
    // 判断章节列表是否加载完成
    protected boolean isChapterListPrepare;

    /**
     * 是否打开过章节
     */
    private boolean isChapterOpen;
    private boolean isFirstOpen = true;
    /**
     *  书籍是否关闭
     */
    private boolean isClose;
    // 页面的翻页效果模式
    private PageMode mPageMode;
    // 加载器的颜色主题
    private PageStyle mPageStyle;
    //当前是否是夜间模式
    private boolean isNightMode;
    //书籍绘制区域的宽高
    private int mVisibleWidth;
    private int mVisibleHeight;
    //应用的宽高
    private int mDisplayWidth;
    private int mDisplayHeight;
    //内容左右间距
    private int mMarginWidth;
    private int mMarginHeight;
    //字体的颜色
    private int mTextColor;
    //标题的大小
    private int mTitleSize;
    //字体的大小
    private int mTextSize;
    //行间距
    private int mTextInterval;
    //标题的行间距
    private int mTitleInterval;
    //段落距离(基于行间距的额外距离)
    private int mTextPara;
    private int mTitlePara;
    //电池的百分比
    private int mBatteryLevel;
    //当前页面的背景
    private int mBgColor;

    // 当前章  在章节列表里的下标
    protected int mCurChapterPos = 0;
    //上一章的记录
    private int mLastChapterPos = 0;

    /*****************************init params*******************************/
    public PageLoader(PageView pageView, CollBookBean collBook) {
        mPageView = pageView;
        mContext = pageView.getContext();
        mCollBook = collBook;
        mChapterList = new ArrayList<>(1);

        // 初始化数据
        initData();
        // 初始化画笔
        initPaint();
        // 初始化PageView
        initPageView();
        // 初始化书籍  从书签里取进度
        prepareBook();
    }

    /**
     * 取偏好设置里的数据 初始化
     */
    private void initData() {
        // 获取配置管理器
        mSettingManager = ReadSettingManager.getInstance();
        // 获取配置参数
        mPageMode = mSettingManager.getPageMode();
        mPageStyle = mSettingManager.getPageStyle();
        // 初始化参数
        mMarginWidth = ScreenUtils.dpToPx(DEFAULT_MARGIN_WIDTH);
        mMarginHeight = ScreenUtils.dpToPx(DEFAULT_MARGIN_HEIGHT);
        // 配置文字有关的参数
        setUpTextParams(mSettingManager.getTextSize());
    }

    /**
     * 作用：设置与文字相关的参数
     *
     * @param textSize
     */
    private void setUpTextParams(int textSize) {
        // 文字大小
        mTextSize = textSize;
        mTitleSize = mTextSize + ScreenUtils.spToPx(EXTRA_TITLE_SIZE);
        // 行间距(大小为字体的一半)
        mTextInterval = mTextSize / 2;
        mTitleInterval = mTitleSize / 2;
        // 段落间距(大小为字体的高度)
        mTextPara = mTextSize;
        mTitlePara = mTitleSize;
    }

    /**
     * 初始化画笔
     */
    private void initPaint() {
        // 绘制提示的画笔
        mTipPaint = new Paint();
        mTipPaint.setColor(mTextColor);
        mTipPaint.setTextAlign(Paint.Align.LEFT); // 绘制的起始点
        mTipPaint.setTextSize(ScreenUtils.spToPx(DEFAULT_TIP_SIZE)); // Tip默认的字体大小
        mTipPaint.setAntiAlias(true);
        mTipPaint.setSubpixelText(true);

        // 绘制页面内容的画笔
        mTextPaint = new TextPaint();
        mTextPaint.setColor(mTextColor);
        mTextPaint.setTextSize(mTextSize);
        mTextPaint.setAntiAlias(true);

        // 绘制标题的画笔
        mTitlePaint = new TextPaint();
        mTitlePaint.setColor(mTextColor);
        mTitlePaint.setTextSize(mTitleSize);
        mTitlePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mTitlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        mTitlePaint.setAntiAlias(true);

        // 绘制背景的画笔
        mBgPaint = new Paint();
        mBgPaint.setColor(mBgColor);

        // 绘制电池的画笔
        mBatteryPaint = new Paint();
        mBatteryPaint.setAntiAlias(true);
        mBatteryPaint.setDither(true);

        // 初始化页面样式
        setNightMode(mSettingManager.isNightMode());
    }

    /**
     * 将偏好设置  给pageView
     */
    private void initPageView() {
        //配置参数
        mPageView.setPageMode(mPageMode);
        mPageView.setBgColor(mBgColor);
    }

    /****************************** public method***************************/
    /**
     * 底部工具栏  点击 上一章  跳转到上一章
     *
     * @return
     */
    public boolean skipPreChapter() {
        // 是否含有上一章
        if (!hasPrevChapter()) {
            return false;
        }
        // 解析装载  上一章数据。完成后 显示绘制
        if (parsePrevChapter()) {
            mCurPage = getCurPage(0);
        } else {
            mCurPage = new TxtPage();
        }
        mPageView.drawCurPage(false);
        return true;
    }

    /**
     * 底部工具栏点击下一章  跳转到下一章
     *
     * @return
     */
    public boolean skipNextChapter() {
        if (!hasNextChapter()) {
            return false;
        }
        Log.e("auto", "---------------skipNextChapter--");
        //判断是否达到章节的终止点
        if (parseNextChapter()) {
            mCurPage = getCurPage(0);
        } else {
            mCurPage = new TxtPage();
        }
        mPageView.drawCurPage(false);
        return true;
    }

    /**
     * 章节列表里点击    跳转到指定章节
     *
     * @param pos:从 0 开始。
     */
    public void skipToChapter(int pos) {
        // 设置参数
        mCurChapterPos = pos;

        // 将上一章的缓存设置为null
        mPrePageList = null;
        // 如果当前下一章缓存正在执行，则取消
        if (mPreLoadDisp != null) {
            mPreLoadDisp.dispose();
        }
        // 将下一章缓存设置为null
        mNextPageTxtList = null;

        // 打开指定章节
        openChapter();
    }

    /**
     * 底部滑动  跳转到本章 指定的页
     *
     * @param pos
     */
    public boolean skipToPage(int pos) {
        if (!isChapterListPrepare) {
            return false;
        }
        mCurPage = getCurPage(pos);
        mPageView.drawCurPage(false);
        return true;
    }

    /**
     * 音量键   翻到上一页
     *
     * @return
     */
    public boolean skipToPrePage() {
        return mPageView.autoPrevPage();
    }

    /**
     * 音量键   翻到下一页
     *
     * @return
     */
    public boolean skipToNextPage() {
        return mPageView.autoNextPage();
    }

    /**
     * 更新时间
     */
    public void updateTime() {
        if (!mPageView.isRunning()) {
            mPageView.drawCurPage(true);
        }
    }

    /**
     * 更新电量
     *
     * @param level
     */
    public void updateBattery(int level) {
        mBatteryLevel = level;

        if (!mPageView.isRunning()) {
            mPageView.drawCurPage(true);
        }
    }

    /**
     * 设置提示的文字大小
     *
     * @param textSize:单位为 px。
     */
    public void setTipTextSize(int textSize) {
        mTipPaint.setTextSize(textSize);

        // 如果屏幕大小加载完成
        mPageView.drawCurPage(false);
    }

    /**
     * 更改文字大小  重新排版绘制
     *
     * @param textSize
     */
    public void setTextSize(int textSize) {
        Log.e(TAG, "--------------setTextSize-");
        // 设置文字相关参数
        setUpTextParams(textSize);

        // 设置画笔的字体大小
        mTextPaint.setTextSize(mTextSize);
        // 设置标题的字体大小
        mTitlePaint.setTextSize(mTitleSize);
        // 存储文字大小
        mSettingManager.setTextSize(mTextSize);
        // 取消缓存
        mPrePageList = null;
        mNextPageTxtList = null;

        // 如果章节列表数据准备好    章节展示过了 切换字体还得排版
        if (isChapterListPrepare && mStatus == STATUS_FINISH) {
            // 重新计算当前页面  重新排版
            dealLoadPageList(mCurChapterPos);

            // 防止在最后一页，通过修改字体，以至于页面数减少导致崩溃的问题
            if (mCurPage.position >= mCurPageTxtList.size()) {
                mCurPage.position = mCurPageTxtList.size() - 1;
            }
            // 重新获取指定页面
            mCurPage = mCurPageTxtList.get(mCurPage.position);
        }

        mPageView.drawCurPage(false);
    }

    /**
     * 设置夜间模式
     *
     * @param nightMode
     */
    public void setNightMode(boolean nightMode) {
        mSettingManager.setNightMode(nightMode);
        isNightMode = nightMode;

        if (isNightMode) {
            mBatteryPaint.setColor(Color.WHITE);
            setPageStyle(PageStyle.NIGHT);
        } else {
            mBatteryPaint.setColor(Color.BLACK);
            setPageStyle(mPageStyle);
        }
    }

    /**
     * 设置页面样式
     *
     * @param pageStyle:页面样式
     */
    public void setPageStyle(PageStyle pageStyle) {
        if (pageStyle != PageStyle.NIGHT) {
            mPageStyle = pageStyle;
            mSettingManager.setPageStyle(pageStyle);
        }

        if (isNightMode && pageStyle != PageStyle.NIGHT) {
            return;
        }

        // 设置当前颜色样式
        mTextColor = ContextCompat.getColor(mContext, pageStyle.getFontColor());
        mBgColor = ContextCompat.getColor(mContext, pageStyle.getBgColor());

        mTipPaint.setColor(mTextColor);
        mTitlePaint.setColor(mTextColor);
        mTextPaint.setColor(mTextColor);

        mBgPaint.setColor(mBgColor);

        mPageView.drawCurPage(false);
    }

    /**
     * 翻页动画
     *
     * @param pageMode:翻页模式
     * @see PageMode
     */
    public void setPageMode(PageMode pageMode) {
        mPageMode = pageMode;

        mPageView.setPageMode(mPageMode);
        mSettingManager.setPageMode(mPageMode);

        // 重新绘制当前页
        mPageView.drawCurPage(false);
    }

    /**
     * 设置自动阅读
     *
     * @param isOpen
     */
    public void setAutoRead(boolean isOpen) {
        // 其他阅读模式下  只能换到 无动画模式才能启动  自动阅读
        if (mPageMode != PageMode.NONE) {
            mPageMode = PageMode.NONE;
            mPageView.setPageMode(mPageMode);
            mSettingManager.setPageMode(mPageMode);

            // 重新绘制当前页
            mPageView.drawCurPage(false);
        }
        mPageView.setAutoRead(isOpen);
    }

    /**
     * 设置内容与屏幕的间距
     *
     * @param marginWidth  :单位为 px
     * @param marginHeight :单位为 px
     */
    public void setMargin(int marginWidth, int marginHeight) {
        mMarginWidth = marginWidth;
        mMarginHeight = marginHeight;

        // 如果是滑动动画，则需要重新创建了
        if (mPageMode == PageMode.SCROLL) {
            mPageView.setPageMode(PageMode.SCROLL);
        }

        mPageView.drawCurPage(false);
    }

    /**
     * 设置页面切换监听
     *
     * @param listener
     */
    public void setOnPageChangeListener(OnPageChangeListener listener) {
        mPageChangeListener = listener;

        // 如果目录加载完之后才设置监听器，那么会默认回调
        if (isChapterListPrepare) {
            mPageChangeListener.onCategoryFinish(mChapterList);
        }
    }

    /**
     * 获取当前页的状态
     *
     * @return
     */
    public int getPageStatus() {
        return mStatus;
    }

    /**
     * 获取书籍信息
     *
     * @return
     */
    public CollBookBean getCollBook() {
        return mCollBook;
    }

    /**
     * 获取章节目录。
     *
     * @return
     */
    public List<TxtChapter> getChapterCategory() {
        return mChapterList;
    }

    /**
     * 获取本章  当前页的页码
     *
     * @return
     */
    public int getPagePos() {
        return mCurPage.position;
    }

    /**
     * 获取当前章节的章节位置 本章章节列表下标
     *
     * @return
     */
    public int getChapterPos() {
        return mCurChapterPos;
    }

    /**
     * 获取距离屏幕的高度
     *
     * @return
     */
    public int getMarginHeight() {
        return mMarginHeight;
    }

    /**
     * 保存书签
     */
    public void saveRecord() {

        if (mChapterList.isEmpty()) {
            return;
        }

        mBookRecord.setBookId(mCollBook.get_id());
        mBookRecord.setChapter(mCurChapterPos);

        if (mCurPage != null) {
            mBookRecord.setPagePos(mCurPage.position);
        } else {
            mBookRecord.setPagePos(0);
        }
        //存储到数据库
        BookRepository.getInstance().saveBookRecord(mBookRecord);
    }

    /**
     * 从书签里取  初始化书籍  书签
     */
    private void prepareBook() {
        mBookRecord = BookRepository.getInstance().getBookRecord(mCollBook.get_id());
        if (mBookRecord == null) {
            mBookRecord = new BookRecordBean();
        }
        mCurChapterPos = mBookRecord.getChapter();
        mLastChapterPos = mCurChapterPos;
    }

    /**
     * 打开当前章节内容
     */
    public void openChapter() {
        // 是否是第一次打开
        isFirstOpen = false;
        // pageView是否初始化完成
        if (!mPageView.isPageViewPrepare()) {
            return;
        }

        // 如果章节目录没有准备好
        if (!isChapterListPrepare) {
            mStatus = STATUS_LOADING;
            mPageView.drawCurPage(false);
            return;
        }

        // 如果获取到的章节目录为空
        if (mChapterList.isEmpty()) {
            mStatus = STATUS_CATEGORY_EMPTY;
            mPageView.drawCurPage(false);
            return;
        }
        // 章节 数据 load成功后  开始解析 排版数据   赋值 本章待绘制的页面列表
        if (parseCurChapter()) {
            // 如果章节从未打开  打开章节   【加 标记 第一次打开取书签】
            if (!isChapterOpen) {
                int position = mBookRecord.getPagePos();

                // 防止记录页的页号，大于当前最大页号
                if (position >= mCurPageTxtList.size()) {
                    position = mCurPageTxtList.size() - 1;
                }
                mCurPage = getCurPage(position);
                mCancelPage = mCurPage;
                // 切换状态
                isChapterOpen = true;
            } else {
                // 不是第一次取第一页
                mCurPage = getCurPage(0);
            }
        } else {
            mCurPage = new TxtPage();
        }
        // 确定了当前page数据  开始绘制
        mPageView.drawCurPage(false);
    }

    /**
     * 章节内容下载失败
     */
    public void chapterError() {
        //加载错误
        mStatus = STATUS_ERROR;
        mPageView.drawCurPage(false);
    }

    /**
     * 关闭书本  释放资源
     */
    public void closeBook() {
        isChapterListPrepare = false;
        isClose = true;

        if (mPreLoadDisp != null) {
            mPreLoadDisp.dispose();
        }

        clearList(mChapterList);
        clearList(mCurPageTxtList);
        clearList(mNextPageTxtList);

        mChapterList = null;
        mCurPageTxtList = null;
        mNextPageTxtList = null;
        mPageView = null;
        mCurPage = null;
    }

    /**
     *  清空列表数据
     * @param list
     */
    private void clearList(List list) {
        if (list != null) {
            list.clear();
        }
    }

    /**
     *  退出界面  会关闭书籍  检测书籍是否关闭
     * @return
     */
    public boolean isClose() {
        return isClose;
    }

    /**
     * 本书是否打开过章节   第一次打开取 书签  非第一次取第一页
     */
    public boolean isChapterOpen() {
        return isChapterOpen;
    }

    /**
     * 加载本章节排版后 的    文字页面列表
     * 1 排版切换后的当前章节
     * 2 预排版下一章
     *
     * @param chapterPos:章节序号
     * @return
     */
    private List<TxtPage> loadPageList(int chapterPos) throws Exception {
        // 获取章节
        TxtChapter chapter = mChapterList.get(chapterPos);
        // 判断本章节是否缓存到本地过
        if (!hasChapterData(chapter)) {
            return null;
        }
        // 先把章节内容缓存到本地文件    再从文件 读取返回流
        BufferedReader reader = getChapterReader(chapter);
        // 将流解析成本章节的文件列表
        List<TxtPage> chapters = loadPages(chapter, reader);
        return chapters;
    }

    /*******************************abstract method***************************************/

    /**
     * 章节列表 本地或者网络  加载完成后   刷新章节列表
     */
    public abstract void refreshChapterList();

    /**
     * 获取章节的文本流
     *
     * @param chapter
     * @return
     */
    protected abstract BufferedReader getChapterReader(TxtChapter chapter) throws Exception;

    /**
     * 章节数据是否存在
     *
     * @return
     */
    protected abstract boolean hasChapterData(TxtChapter chapter);

    /***********************************default method***********************************************/

    /**
     * 绘制阅读页
     *
     * @param bitmap                要绘制的位图
     * @param isUpdateBatteryOrTime 是否是更新时间 电池
     */
    void drawPage(Bitmap bitmap, boolean isUpdateBatteryOrTime) {
        Log.e(TAG, "--------------------drawPage 执行将文字绘制到bitmap中");
        // 绘制背景
        drawBackground(mPageView.getBgBitmap(), isUpdateBatteryOrTime);
        // 绘制文字内容
        if (!isUpdateBatteryOrTime) {
            drawContent(bitmap);
        }
        //更新绘制
        mPageView.invalidate();
    }

    /**
     * 绘制背景   标题  电池到指定bitmap上
     *
     * @param bitmap                要绘制的bitmap
     * @param isUpdateBatteryOrTime
     */
    private void drawBackground(Bitmap bitmap, boolean isUpdateBatteryOrTime) {
        Canvas canvas = new Canvas(bitmap);
        int tipMarginHeight = ScreenUtils.dpToPx(3);
        if (!isUpdateBatteryOrTime) {
            /****绘制背景****/
            canvas.drawColor(mBgColor);

            if (!mChapterList.isEmpty()) {
                /*****初始化标题的参数********/
                //需要注意的是:绘制text的y的起始点是text的基准线的位置，而不是从text的头部的位置
                float tipTop = tipMarginHeight - mTipPaint.getFontMetrics().top;
                //绘制标题 右上角          根据状态不一样，数据不一样
                if (mStatus != STATUS_FINISH) {
                    if (isChapterListPrepare) {
                        canvas.drawText(mChapterList.get(mCurChapterPos).getTitle(), mMarginWidth, tipTop, mTipPaint);
                    }
                } else {
                    canvas.drawText(mCurPage.title, mMarginWidth, tipTop, mTipPaint);
                }

                /******绘制页码********/
                // 底部的字显示的位置Y
                float y = mDisplayHeight - mTipPaint.getFontMetrics().bottom - tipMarginHeight;
                // 只有finish的时候采用页码
                if (mStatus == STATUS_FINISH) {
                    String percent = (mCurPage.position + 1) + "/" + mCurPageTxtList.size();
                    canvas.drawText(percent, mMarginWidth, y, mTipPaint);
                }
            }
        } else {
            //擦除区域
            mBgPaint.setColor(mBgColor);
            canvas.drawRect(mDisplayWidth / 2, mDisplayHeight - mMarginHeight + ScreenUtils.dpToPx(2), mDisplayWidth, mDisplayHeight, mBgPaint);
        }

        /******绘制电池********/

        int visibleRight = mDisplayWidth - mMarginWidth;
        int visibleBottom = mDisplayHeight - tipMarginHeight;

        int outFrameWidth = (int) mTipPaint.measureText("xxx");
        int outFrameHeight = (int) mTipPaint.getTextSize();

        int polarHeight = ScreenUtils.dpToPx(6);
        int polarWidth = ScreenUtils.dpToPx(2);
        int border = 1;
        int innerMargin = 1;

        //电极的制作
        int polarLeft = visibleRight - polarWidth;
        int polarTop = visibleBottom - (outFrameHeight + polarHeight) / 2;
        Rect polar = new Rect(polarLeft, polarTop, visibleRight,
                polarTop + polarHeight - ScreenUtils.dpToPx(2));

        mBatteryPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(polar, mBatteryPaint);

        //外框的制作
        int outFrameLeft = polarLeft - outFrameWidth;
        int outFrameTop = visibleBottom - outFrameHeight;
        int outFrameBottom = visibleBottom - ScreenUtils.dpToPx(2);
        Rect outFrame = new Rect(outFrameLeft, outFrameTop, polarLeft, outFrameBottom);

        mBatteryPaint.setStyle(Paint.Style.STROKE);
        mBatteryPaint.setStrokeWidth(border);
        canvas.drawRect(outFrame, mBatteryPaint);

        //内框的制作
        float innerWidth = (outFrame.width() - innerMargin * 2 - border) * (mBatteryLevel / 100.0f);
        RectF innerFrame = new RectF(outFrameLeft + border + innerMargin, outFrameTop + border + innerMargin,
                outFrameLeft + border + innerMargin + innerWidth, outFrameBottom - border - innerMargin);

        mBatteryPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(innerFrame, mBatteryPaint);

        /******绘制当前时间********/
        //底部的字显示的位置Y
        float y = mDisplayHeight - mTipPaint.getFontMetrics().bottom - tipMarginHeight;
        String time = StringUtils.dateConvert(System.currentTimeMillis(), Constant.FORMAT_TIME);
        float x = outFrameLeft - mTipPaint.measureText(time) - ScreenUtils.dpToPx(4);
        canvas.drawText(time, x, y, mTipPaint);
    }

    /**
     * 绘制具体文字内容
     *
     * @param bitmap
     */
    private void drawContent(Bitmap bitmap) {
        Log.e(TAG, "----------------------当前执行绘制第一行为：" + bitmap.getClass());
        Canvas canvas = new Canvas(bitmap);

        // 上下滑动的话先绘制背景色    其他的翻页模式 以下一页为背景色
        if (mPageMode == PageMode.SCROLL) {
            canvas.drawColor(mBgColor);
        }
        /******绘制内容****/

        if (mStatus != STATUS_FINISH) {
            //绘制缺省提示
            String tip = "";
            switch (mStatus) {
                case STATUS_LOADING:
                    tip = "正在拼命加载中...";
                    break;
                case STATUS_ERROR:
                    tip = "加载失败(点击边缘重试)";
                    break;
                case STATUS_EMPTY:
                    tip = "文章内容为空";
                    break;
                case STATUS_PARING:
                    tip = "正在排版请等待...";
                    break;
                case STATUS_PARSE_ERROR:
                    tip = "文件解析错误";
                    break;
                case STATUS_CATEGORY_EMPTY:
                    tip = "目录列表为空";
                    break;
                default:
                    break;
            }

            //将提示语句放到正中间
            Paint.FontMetrics fontMetrics = mTextPaint.getFontMetrics();
            float textHeight = fontMetrics.top - fontMetrics.bottom;
            float textWidth = mTextPaint.measureText(tip);
            float pivotX = (mDisplayWidth - textWidth) / 2;
            float pivotY = (mDisplayHeight - textHeight) / 2;
            canvas.drawText(tip, pivotX, pivotY, mTextPaint);
        } else {
            // 绘制正文内容
            float top;

            if (mPageMode == PageMode.SCROLL) {
                top = -mTextPaint.getFontMetrics().top;
            } else {
                top = mMarginHeight - mTextPaint.getFontMetrics().top;
            }

            //设置总距离
            int interval = mTextInterval + (int) mTextPaint.getTextSize();
            int para = mTextPara + (int) mTextPaint.getTextSize();
            int titleInterval = mTitleInterval + (int) mTitlePaint.getTextSize();
            int titlePara = mTitlePara + (int) mTextPaint.getTextSize();
            String str = null;

            //1 对标题进行绘制  标题也在lines中  先将前几行标题绘制完成后 再绘制内容
            for (int i = 0; i < mCurPage.titleLines; ++i) {
                str = mCurPage.lines.get(i);

                //设置顶部间距
                if (i == 0) {
                    top += mTitlePara;
                }

                //计算文字显示的起始点   起始居中绘制
                int start = (int) (mDisplayWidth - mTitlePaint.measureText(str)) / 2;
                //进行绘制
                canvas.drawText(str, start, top, mTitlePaint);

                //设置尾部间距
                if (i == mCurPage.titleLines - 1) {
                    top += titlePara;
                } else {
                    //行间距
                    top += titleInterval;
                }
            }

            //2  对内容进行绘制  除去标题的行数  就是内容行数
            for (int i = mCurPage.titleLines; i < mCurPage.lines.size(); ++i) {
                str = mCurPage.lines.get(i);
                if (i == mCurPage.titleLines) {
                    Log.e(TAG, "----------------------当前执行绘制第一行为：" + str);
                }
                canvas.drawText(str, mMarginWidth, top, mTextPaint);
                if (str.endsWith("\n")) {
                    top += para;
                } else {
                    top += interval;
                }
            }
        }
    }

    /**
     * 准备展示相关的设置
     *
     * @param w 屏幕宽
     * @param h 屏幕高
     */
    void prepareDisplay(int w, int h) {
        Log.e(TAG, "--------------prepareDisplay-");
        // 获取PageView的宽高
        mDisplayWidth = w;
        mDisplayHeight = h;

        // 获取内容显示位置的大小
        mVisibleWidth = mDisplayWidth - mMarginWidth * 2;
        mVisibleHeight = mDisplayHeight - mMarginHeight * 2;

        // 重置 PageMode
        mPageView.setPageMode(mPageMode);

        if (!isChapterOpen) {
            // 展示加载界面
            mPageView.drawCurPage(false);
            // 如果在 display 之前调用过 openChapter 肯定是无法打开的。
            // 所以需要通过 display 再重新调用一次。
            if (!isFirstOpen) {
                // 打开书籍
                openChapter();
            }
        } else {
            // 如果章节已显示，那么就重新计算页面
            if (mStatus == STATUS_FINISH) {
                // 显示区域变了  重新排版
                dealLoadPageList(mCurChapterPos);
                // 重新设置文章指针的位置
                mCurPage = getCurPage(mCurPage.position);
            }
            mPageView.drawCurPage(false);
        }
    }

    /**
     * 根据数据源判断是否有上一页  并翻到上一页  绘制上一页
     *
     * @return
     */
    boolean prev() {
        // 以下情况禁止翻页
        if (!canTurnPage()) {
            return false;
        }

        if (mStatus == STATUS_FINISH) {
            // 先查看是否存在上一页
            TxtPage prevPage = getPrevPage();
            if (prevPage != null) {
                mCancelPage = mCurPage;
                mCurPage = prevPage;
                mPageView.drawNextPage();
                return true;
            }
        }

        if (!hasPrevChapter()) {
            return false;
        }

        mCancelPage = mCurPage;
        // 本章没有上一页  加载上一章  绘制上一章最后一页
        if (parsePrevChapter()) {
            mCurPage = getPrevLastPage();
        } else {
            mCurPage = new TxtPage();
        }
        mPageView.drawNextPage();
        return true;
    }

    /**
     * 解析上一章数据 即将绘制的上一章文字界面  【1 点击上一章 2 翻页翻到上一章 3取消翻页时】
     *
     * @return:数据是否解析成功
     */
    boolean parsePrevChapter() {
        // 往前翻时   【处理当前  和下一章的逻辑  交换数据集】
        // 加载上一章数据
        int prevChapter = mCurChapterPos - 1;
        mLastChapterPos = mCurChapterPos;
        mCurChapterPos = prevChapter;
        // 当前章 切换成前一章   当前章列表   存为下一章列表数据
        mNextPageTxtList = mCurPageTxtList;


        // 判断是否具有上一章列表页是否处理完成   未完成 先排版
        if (mPrePageList != null) {
            // 上一章文字界面列表排版完成   直接回调 显示
            mCurPageTxtList = mPrePageList;
            mPrePageList = null;
            // 章节切换回调
            chapterChangeCallback();
        } else {
            // 前一章节未完成排版   先排版
            dealLoadPageList(prevChapter);
        }
        Log.e(TAG, "--------------parsePrevChapter 解析上一章数据");
        return mCurPageTxtList != null ? true : false;
    }

    /**
     * 根据章节下标判断是否含有上一章节
     *
     * @return
     */
    private boolean hasPrevChapter() {
        //判断是否上一章节为空
        if (mCurChapterPos - 1 < 0) {
            return false;
        }
        return true;
    }

    /**
     * 判断数据源是否含有下一页   【将数据绘制到bitmap上】
     *
     * @return:是否允许翻页
     */
    boolean next() {
        // 以下情况禁止翻页
        if (!canTurnPage()) {
            return false;
        }
        // 1 本章有下一页  向nextBitmap绘制下一页文字
        if (mStatus == STATUS_FINISH) {
            // 先查看本章是否存在下一页
            TxtPage nextPage = getNextPage();
            if (nextPage != null) {
                mCancelPage = mCurPage;
                mCurPage = nextPage;
                mPageView.drawNextPage();
                return true;
            }
        }
        // 2 本章没有下一页    查看是否有下一章节
        if (!hasNextChapter()) {
            return false;
        }

        mCancelPage = mCurPage;
        Log.e("auto", "--------------next()");
        // 3 解析下一章数据  绘制下一章第一页
        if (parseNextChapter()) {
            mCurPage = mCurPageTxtList.get(0);
        } else {
            mCurPage = new TxtPage();
        }
        mPageView.drawNextPage();
        return true;
    }

    /**
     * 根据章节下标判断是否含有下一章节
     *
     * @return
     */
    private boolean hasNextChapter() {
        // 判断是否到达目录最后一章
        if (mCurChapterPos + 1 >= mChapterList.size()) {
            return false;
        }
        return true;
    }

    /**
     * 章节加载完成后  开始解析数据  解析当前章节
     *
     * @return
     */
    boolean parseCurChapter() {
        Log.e("auto", "--------------parseCurChapter");
        // 解析数据  排版  解析成 文字列表后 通知展示
        dealLoadPageList(mCurChapterPos);
        // 预排版下一章
        preLoadNextChapter();
        return mCurPageTxtList != null ? true : false;
    }

    /**
     * 解析下一章数据
     *
     * @return:返回解析成功还是失败
     */
    boolean parseNextChapter() {
        // 处理翻到下一章后的数据源切换
        int nextChapter = mCurChapterPos + 1;
        mLastChapterPos = mCurChapterPos;
        mCurChapterPos = nextChapter;
        // 将当前章的页面列表，作为上一章缓存
        mPrePageList = mCurPageTxtList;


        // 是否下一章数据已经预加载了 判断完成
        if (mNextPageTxtList != null) {
            mCurPageTxtList = mNextPageTxtList;
            mNextPageTxtList = null;
            // 回调章节切换了
            chapterChangeCallback();
        } else {
            // 未排版解析 先排版解析
            dealLoadPageList(nextChapter);
        }
        // 预排版下一章
        preLoadNextChapter();
        Log.e("auto", "--------------parseNextChapter");
        return mCurPageTxtList != null ? true : false;
    }

    /**
     * 解析指定章节 数据 【包括排版】
     * 1 字体变化
     * 2 界面大小变化
     * 3 切换上一章节时 上一章未排版 得先排版
     * 4 当前章节数据load完成 解析当前章节数据
     * 5 同3翻到下一章了
     *
     * @param chapterPos
     */
    private void dealLoadPageList(int chapterPos) {
        try {
            // 排版 返回本章 文字列表页
            mCurPageTxtList = loadPageList(chapterPos);
            if (mCurPageTxtList != null) {
                // 排版完成  数据为空
                if (mCurPageTxtList.isEmpty()) {
                    mStatus = STATUS_EMPTY;

                    // 添加一个空数据
                    TxtPage page = new TxtPage();
                    page.lines = new ArrayList<>(1);
                    mCurPageTxtList.add(page);
                } else {
                    // 排版完成后  将总状态 设置已完成
                    mStatus = STATUS_FINISH;
                }
            } else {
                // 排版未完成  得重新下载章节数据
                mStatus = STATUS_LOADING;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 排版报错了
            mCurPageTxtList = null;
            mStatus = STATUS_ERROR;
        }
        Log.e(TAG, "--------------dealLoadPageList  处理章节排版");
        // 回调
        chapterChangeCallback();
    }

    /**
     * 切换章节时 回调  更新章节列表ui  和 底部页码ui
     */
    private void chapterChangeCallback() {
        Log.e(TAG, "--------------chapterChangeCallback章节切换 回调了");
        if (mPageChangeListener != null) {
            mPageChangeListener.onChapterChange(mCurChapterPos);
            mPageChangeListener.onPageCountChange(mCurPageTxtList != null ? mCurPageTxtList.size() : 0);
        }
    }

    /**
     * 预排版下一章数据  赋值给mNextPageTxtList  下一章页面列表
     */
    private void preLoadNextChapter() {
        int nextChapter = mCurChapterPos + 1;

        // 如果不存在下一章，且下一章没有数据，则不进行加载。
        if (!hasNextChapter() || !hasChapterData(mChapterList.get(nextChapter))) {
            return;
        }

        //如果之前正在加载则取消
        if (mPreLoadDisp != null) {
            mPreLoadDisp.dispose();
        }

        //调用异步进行预加载加载
        Single.create(new SingleOnSubscribe<List<TxtPage>>() {
            @Override
            public void subscribe(SingleEmitter<List<TxtPage>> e) throws Exception {
                e.onSuccess(loadPageList(nextChapter));
            }
        }).compose(RxUtils::toSimpleSingle)
                .subscribe(new SingleObserver<List<TxtPage>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        mPreLoadDisp = d;
                    }

                    @Override
                    public void onSuccess(List<TxtPage> pages) {
                        mNextPageTxtList = pages;
                    }

                    @Override
                    public void onError(Throwable e) {
                        //无视错误
                    }
                });
    }

    /**
     * 取消翻页
     */
    void pageCancel() {
        if (mCurPage.position == 0 && mCurChapterPos > mLastChapterPos) { // 加载到下一章取消了
            if (mPrePageList != null) {
                cancelNextChapter();
            } else {
                if (parsePrevChapter()) {
                    mCurPage = getPrevLastPage();
                } else {
                    mCurPage = new TxtPage();
                }
            }
        } else if (mCurPageTxtList == null
                || (mCurPage.position == mCurPageTxtList.size() - 1
                && mCurChapterPos < mLastChapterPos)) {  // 加载上一章取消了

            if (mNextPageTxtList != null) {
                cancelPreChapter();
            } else {
                Log.e("auto", "--------------pageCancel");
                if (parseNextChapter()) {
                    mCurPage = mCurPageTxtList.get(0);
                } else {
                    mCurPage = new TxtPage();
                }
            }
        } else {
            // 假设加载到下一页，又取消了。那么需要重新装载。
            mCurPage = mCancelPage;
        }
    }

    private void cancelNextChapter() {
        int temp = mLastChapterPos;
        mLastChapterPos = mCurChapterPos;
        mCurChapterPos = temp;

        mNextPageTxtList = mCurPageTxtList;
        mCurPageTxtList = mPrePageList;
        mPrePageList = null;
        Log.e("auto", "--------------cancelNextChapter");
        chapterChangeCallback();

        mCurPage = getPrevLastPage();
        mCancelPage = null;
    }

    private void cancelPreChapter() {
        // 重置位置点
        int temp = mLastChapterPos;
        mLastChapterPos = mCurChapterPos;
        mCurChapterPos = temp;
        // 重置页面列表
        mPrePageList = mCurPageTxtList;
        mCurPageTxtList = mNextPageTxtList;
        mNextPageTxtList = null;
        Log.e("auto", "--------------cancelPreChapter");
        chapterChangeCallback();

        mCurPage = getCurPage(0);
        mCancelPage = null;
    }

    /**************************************private method********************************************/
    /**
     * 将本章节数据流，解析成本章节  页面列表  【排版】
     *
     * @param chapter：章节信息
     * @param br：章节的文本流
     * @return
     */
    private List<TxtPage> loadPages(TxtChapter chapter, BufferedReader br) {
        //生成的页面
        List<TxtPage> pages = new ArrayList<>();
        //使用流的方式加载 本章总文字行数
        List<String> lines = new ArrayList<>();
        // 可显示文字的高度  用于排版
        int rHeight = mVisibleHeight;
        int titleLinesCount = 0;
        // 是否展示标题
        boolean showTitle = true;
        //默认展示标题
        String paragraph = chapter.getTitle();
        try {
            while (showTitle || (paragraph = br.readLine()) != null) {
                paragraph = StringUtils.convertCC(paragraph, mContext);
                // 重置段落
                if (!showTitle) {
                    paragraph = paragraph.replaceAll("\\s", "");
                    // 如果只有换行符，那么就不执行
                    if (paragraph.equals("")) continue;
                    paragraph = StringUtils.halfToFull("  " + paragraph + "\n");
                } else {
                    //设置 title 的顶部间距
                    rHeight -= mTitlePara;
                }
                int wordCount = 0;
                String subStr = null;
                while (paragraph.length() > 0) {
                    //当前空间，是否容得下一行文字
                    if (showTitle) {
                        rHeight -= mTitlePaint.getTextSize();
                    } else {
                        rHeight -= mTextPaint.getTextSize();
                    }
                    // 一页已经填充满了，创建 TextPage
                    if (rHeight <= 0) {
                        // 创建Page
                        TxtPage page = new TxtPage();
                        page.position = pages.size();
                        page.title = StringUtils.convertCC(chapter.getTitle(), mContext);
                        page.lines = new ArrayList<>(lines);
                        page.titleLines = titleLinesCount;
                        pages.add(page);
                        // 重置Lines
                        lines.clear();
                        rHeight = mVisibleHeight;
                        titleLinesCount = 0;

                        continue;
                    }

                    //测量一行占用的字节数
                    if (showTitle) {
                        wordCount = mTitlePaint.breakText(paragraph,
                                true, mVisibleWidth, null);
                    } else {
                        wordCount = mTextPaint.breakText(paragraph,
                                true, mVisibleWidth, null);
                    }

                    subStr = paragraph.substring(0, wordCount);
                    if (!subStr.equals("\n")) {
                        //将一行字节，存储到lines中
                        lines.add(subStr);

                        //设置段落间距
                        if (showTitle) {
                            titleLinesCount += 1;
                            rHeight -= mTitleInterval;
                        } else {
                            rHeight -= mTextInterval;
                        }
                    }
                    //裁剪
                    paragraph = paragraph.substring(wordCount);
                }

                //增加段落的间距
                if (!showTitle && lines.size() != 0) {
                    rHeight = rHeight - mTextPara + mTextInterval;
                }

                if (showTitle) {
                    rHeight = rHeight - mTitlePara + mTitleInterval;
                    showTitle = false;
                }
            }

            if (lines.size() != 0) {
                //创建Page
                TxtPage page = new TxtPage();
                page.position = pages.size();
                page.title = StringUtils.convertCC(chapter.getTitle(), mContext);
                page.lines = new ArrayList<>(lines);
                page.titleLines = titleLinesCount;
                pages.add(page);
                //重置Lines
                lines.clear();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(br);
        }
        return pages;
    }


    /**
     * 获取当前章节  指定下标页码的 页面
     */
    private TxtPage getCurPage(int pos) {
        if (mPageChangeListener != null) {
            mPageChangeListener.onPageChange(pos);
        }
        return mCurPageTxtList.get(pos);
    }

    /**
     * 获取本章  上一个页面
     */
    private TxtPage getPrevPage() {
        int pos = mCurPage.position - 1;
        if (pos < 0) {
            return null;
        }
        if (mPageChangeListener != null) {
            mPageChangeListener.onPageChange(pos);
        }
        return mCurPageTxtList.get(pos);
    }

    /**
     * 获取本章  下一页 页面 的  数据   [TxtPage 一页的数据源]
     */
    private TxtPage getNextPage() {
        int pos = mCurPage.position + 1;
        // mCurPageTxtList 当前章节
        if (pos >= mCurPageTxtList.size()) {
            return null;
        }
        if (mPageChangeListener != null) {
            mPageChangeListener.onPageChange(pos);
        }
        return mCurPageTxtList.get(pos);
    }

    /**
     * 获取上一个章节的最后一页
     */
    private TxtPage getPrevLastPage() {
        int pos = mCurPageTxtList.size() - 1;

        if (mPageChangeListener != null) {
            mPageChangeListener.onPageChange(pos);
        }

        return mCurPageTxtList.get(pos);
    }

    /**
     * 根据当前状态，决定是否能够翻页
     *
     * @return
     */
    private boolean canTurnPage() {
        // 章节列表未获取完成 
        if (!isChapterListPrepare) {
            return false;
        }
        // 判断解析状态
        if (mStatus == STATUS_PARSE_ERROR || mStatus == STATUS_PARING) {
            return false;
        } else if (mStatus == STATUS_ERROR) {
            mStatus = STATUS_LOADING;
        }
        return true;
    }

    /*****************************************interface*****************************************/

    public interface OnPageChangeListener {
        /**
         * 作用：章节切换的时候进行回调
         *
         * @param pos:切换章节的序号
         */
        void onChapterChange(int pos);

        /**
         * 作用：请求加载章节内容
         *
         * @param requestChapters:需要下载的章节列表
         */
        void requestChapters(List<TxtChapter> requestChapters);

        /**
         * 作用：章节目录加载完成时候回调
         *
         * @param chapters：返回章节目录
         */
        void onCategoryFinish(List<TxtChapter> chapters);

        /**
         * 作用：章节页码数量改变之后的回调。==> 字体大小的调整，或者是否关闭虚拟按钮功能都会改变页面的数量。
         *
         * @param count:页面的数量
         */
        void onPageCountChange(int count);

        /**
         * 作用：当页面改变的时候回调
         *
         * @param pos:当前的页面的序号
         */
        void onPageChange(int pos);
    }
}
