package com.example.newbiechen.ireader.widget.page;


import android.util.Log;

import com.example.newbiechen.ireader.model.bean.BookChapterBean;
import com.example.newbiechen.ireader.model.bean.CollBookBean;
import com.example.newbiechen.ireader.model.local.BookRepository;
import com.example.newbiechen.ireader.ui.activity.ReadActivity;
import com.example.newbiechen.ireader.utils.BookManager;
import com.example.newbiechen.ireader.utils.Constant;
import com.example.newbiechen.ireader.utils.FileUtils;
import com.example.newbiechen.ireader.utils.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by newbiechen on 17-5-29.
 * 网络页面加载器
 */

public class NetPageLoader extends PageLoader {
    private static final String TAG = "PageFactory";

    public NetPageLoader(PageView pageView, CollBookBean collBook) {
        super(pageView, collBook);
    }

    private List<TxtChapter> convertTxtChapter(List<BookChapterBean> bookChapters) {
        List<TxtChapter> txtChapters = new ArrayList<>(bookChapters.size());
        for (BookChapterBean bean : bookChapters) {
            TxtChapter chapter = new TxtChapter();
            chapter.bookId = bean.getBookId();
            chapter.title = bean.getTitle();
            chapter.link = bean.getLink();
            txtChapters.add(chapter);
        }
        return txtChapters;
    }

    /**
     * 章节列表数据回调   刷新
     */
    @Override
    public void refreshChapterList() {
        if (mCollBook.getBookChapters() == null) return;

        // 将 BookChapter 转换成当前可用的 Chapter
        mChapterList = convertTxtChapter(mCollBook.getBookChapters());
        isChapterListPrepare = true;

        // 目录加载完成，执行回调操作。
        if (mPageChangeListener != null) {
            mPageChangeListener.onCategoryFinish(mChapterList);
        }

        // 如果章节未打开
        if (!isChapterOpen()) {
            // 打开章节
            openChapter();
        }
    }

    /**
     * 获取指定章节    章节内容返回流文件
     *
     * @param chapter
     * @return
     * @throws Exception
     */
    @Override
    protected BufferedReader getChapterReader(TxtChapter chapter) throws Exception {
        File file = new File(Constant.BOOK_CACHE_PATH + mCollBook.get_id()
                + File.separator + chapter.title + FileUtils.SUFFIX_NB);
        if (!file.exists()) return null;

        Reader reader = new FileReader(file);
        BufferedReader br = new BufferedReader(reader);
        return br;
    }

    /**
     * 是否含有章节缓存【判断本地是否缓存过本章节的文件】
     *
     * @param chapter
     * @return
     */
    @Override
    protected boolean hasChapterData(TxtChapter chapter) {
        return BookManager.isChapterCached(mCollBook.get_id(), chapter.title);
    }

    /**
     * 解析上一章数据   把章节排版
     */
    @Override
    boolean parsePrevChapter() {
        // 调用super 解析上一章  是否完成
        boolean isRight = super.parsePrevChapter();

        // 上一章数据解析完成后  已经将上一章下标赋值为本章
        if (mStatus == STATUS_FINISH) {
            // 预请求上一章数据
            loadPrevChapter();
        } else if (mStatus == STATUS_LOADING) {
            // 数据根本没缓存过   重新请求本章节的数据  再来解析
            loadCurrentChapter();
        }
        return isRight;
    }

    /**
     * 解析当前章节数据   排版成页面列表
     */
    @Override
    boolean parseCurChapter() {
        // 先调父类方法尝试解析排版
        boolean isRight = super.parseCurChapter();
        // 排版后 看是否需要去下载
        if (mStatus == STATUS_LOADING) {
            // 本章未排版成功的话  那就去下载本章数据
            loadCurrentChapter();
        }
        return isRight;
    }

    /**
     * 解析下一章数据  排版
     */
    @Override
    boolean parseNextChapter() {
        Log.e("auto", "--------------parseNextChapter-parseNextChapter--isMainThread()" + ReadActivity.isMainThread());
        boolean isRight = super.parseNextChapter();

        if (mStatus == STATUS_FINISH) {
            loadNextChapter();
        } else if (mStatus == STATUS_LOADING) {
            loadCurrentChapter();
        }

        return isRight;
    }

    /**
     * 加载当前页的前面个章节
     */
    private void loadPrevChapter() {
        if (mPageChangeListener != null) {
            int end = mCurChapterPos;
            int begin = end - 2;
            if (begin < 0) {
                begin = 0;
            }
            requestChapters(begin, end);
        }
    }

    /**
     * 下载 当前章节
     *  1 解析上一章时发现数据不存在 没法解析  先下载这章的数据、
     *  2 解析下一章时~
     *  3 解析本章时~
     */
    private void loadCurrentChapter() {
        if (mPageChangeListener != null) {
            int begin = mCurChapterPos;
            int end = mCurChapterPos;

            // 是否当前不是最后一章
            if (end < mChapterList.size()) {
                end = end + 1;
                if (end >= mChapterList.size()) {
                    end = mChapterList.size() - 1;
                }
            }

            // 如果当前不是第一章
            if (begin != 0) {
                begin = begin - 1;
                if (begin < 0) {
                    begin = 0;
                }
            }

            requestChapters(begin, end);
        }
    }

    /**
     * 加载当前页的后两个章节
     */
    private void loadNextChapter() {
        if (mPageChangeListener != null) {

            // 提示加载后两章
            int begin = mCurChapterPos + 1;
            int end = begin + 1;

            // 判断是否大于最后一章
            if (begin >= mChapterList.size()) {
                // 如果下一章超出目录了，就没有必要加载了
                return;
            }

            if (end > mChapterList.size()) {
                end = mChapterList.size() - 1;
            }

            requestChapters(begin, end);
        }
    }

    /**
     * 进行网络请求加载章节内容
     *
     * @param start 要加载的起始章节 下标
     * @param end   要加载的结束章节 下标
     */
    private void requestChapters(int start, int end) {
        // 检验输入值
        if (start < 0) {
            start = 0;
        }

        if (end >= mChapterList.size()) {
            end = mChapterList.size() - 1;
        }


        List<TxtChapter> chapters = new ArrayList<>();

        // 过滤，哪些数据已经加载了
        for (int i = start; i <= end; ++i) {
            TxtChapter txtChapter = mChapterList.get(i);
            // 判断本地是否含有本章节的数据
            if (!hasChapterData(txtChapter)) {
                chapters.add(txtChapter);
            }
        }
        // 传入要下载的章节集合    进行请求数据
        if (!chapters.isEmpty()) {
            mPageChangeListener.requestChapters(chapters);
        }
    }

    @Override
    public void saveRecord() {
        super.saveRecord();
        if (mCollBook != null && isChapterListPrepare) {
            //表示当前CollBook已经阅读
            mCollBook.setIsUpdate(false);
            mCollBook.setLastRead(StringUtils.
                    dateConvert(System.currentTimeMillis(), Constant.FORMAT_BOOK_DATE));
            //直接更新
            BookRepository.getInstance()
                    .saveCollBook(mCollBook);
        }
    }
}

