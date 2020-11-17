/*
 * Copyright (C) 2019 Aayat Mimiko
 */

package com.atmko.stack;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import static com.atmko.stack.NetworkFunctions.isOnline;

@SuppressWarnings("unchecked")
public class Stack extends RecyclerView.OnScrollListener {
    //stack operation identifiers
    public static final int GO_DOWN_ONE_BLOCK = 1;
    public static final int GO_UP_ONE_BLOCK = 2;

    private final int mFirstPage;
    private int mTotalPages;
    private final int mBlockLimit;
    private final Object mPreloadObject;
    private final PagingBlockTemplate mPagingBlockTemplate;
    private final RecyclerView mRecyclerView;
    private final RecyclerView.Adapter mAdapter;
    private final boolean mUsesInternet;
    private final SparseArray<PagingBlock> mPagingBlockMap;
    private boolean mIsIdle;

    private StackMethods mStackMethods;

    public Stack(boolean pageZeroStart, int blockLimit, PagingBlockTemplate pagingBlockTemplate,
                 Object preloadObject, RecyclerView recyclerView, RecyclerView.Adapter adapter,
                 boolean usesInternet) {

        if (!(adapter instanceof StackMethods)) throw new Error("Adapter must implement StackMethods");

        this.mFirstPage = pageZeroStart ? 0 : 1;
        this.mBlockLimit = blockLimit;
        this.mPagingBlockTemplate = pagingBlockTemplate;
        this.mPreloadObject = preloadObject;
        this.mRecyclerView = recyclerView;
        this.mAdapter = adapter;
        this.mUsesInternet = usesInternet;
        this.mPagingBlockMap = new SparseArray<>();

        mStackMethods = (StackMethods) adapter;

        mIsIdle = true;
    }


    public interface StackMethods {
        List getAdapterData();
    }

    private boolean isAdapterEmpty() {
        return mStackMethods.getAdapterData().size() == 0;
    }

    private SparseArray<PagingBlock> getPagingBlockMap() {
        return mPagingBlockMap;
    }

    public boolean isIdle() {
        return mIsIdle;
    }

    public int[] saveBlockStructure() {
        int[] blockIndexRange = {0, 0};

        if (mPagingBlockMap.size() == 0) return blockIndexRange;

        blockIndexRange[0] = mPagingBlockMap.keyAt(0);

        int lastBlockNumber = mPagingBlockMap.keyAt(mPagingBlockMap.size() - 1);
        //index range[1] not inclusive in operation.
        //therefore + 1 is added to include last block
        int rangeAdjustment = lastBlockNumber + 1;

        blockIndexRange[1] = rangeAdjustment;

        return blockIndexRange;
    }

    public void restorePagingBlockStructure(int[] blockIndexRange, List fullDataList) {
        //index range[1] not inclusive in operation.
        //e.g range of: 1, 4 generates 3 values (1, 2, 3)
        int iterationSize = blockIndexRange[1] - blockIndexRange[0];

        for (int index = 0; index < iterationSize; index++) {
            int blockIndex = blockIndexRange[0] + index;
            PagingBlock pagingBlock =
                    new PagingBlock(getFirstPage(), blockIndex,
                            mPagingBlockTemplate.getBlockPageCapacity());

            restorePagingBlockPages(pagingBlock, fullDataList);

            mPagingBlockMap.put(blockIndex, pagingBlock);
        }
    }

    private void restorePagingBlockPages(PagingBlock pagingBlock, List fullDataList) {
        int firstPageInBlock = pagingBlock.getFirstPageInBlock();

        //iterate through pageCapacity size
        for (int i = 0; i < pagingBlock.getBlockPageCapacity(); i++) {
            int pageNumber = firstPageInBlock + i;

            int iterationSize = Math.min(fullDataList.size(), mPagingBlockTemplate.pageCapacity);

            List dataList = new ArrayList();

            //populate dataList for this page
            for (int x = 0; x < iterationSize; x++) {
                dataList.add(fullDataList.get(0));
                fullDataList.remove(0);

            }

            pagingBlock.setDataListByPage(pageNumber, dataList);
        }

    }

    private int getTotalPages() {
        return this.mTotalPages;
    }

    public void setTotalPages(int totalPages) {
        this.mTotalPages = totalPages;
    }

    //initial setup paging block
    public void initialize() {
        //stack is not idle
        mIsIdle = false;

        //clear values
        mPagingBlockMap.clear();
        mStackMethods.getAdapterData().clear();

        mAdapter.notifyDataSetChanged();
        mTotalPages = 0;

        //load new block
        loadNextBlock(0);
    }

    public int getFirstPage() {
        return mFirstPage;
    }

    public void setIsFrozen(boolean isFrozen) {
        this.mRecyclerView.setLayoutFrozen(isFrozen);

    }

    //this method is called as many times as the value of blockPageCapacity
    public void stackPage(int blockNumber, int pageNumber, List dataList, int stackOperation) {
        //get blocks for stacking
        PagingBlock pagingBlock = mPagingBlockMap.get(blockNumber);

        //if data list is null
        //define data list as a list of preload objects so they can be stacked without incident
        if (dataList == null) {
            dataList = new ArrayList();
            for (int i = 0; i < mPagingBlockTemplate.pageCapacity ; i++) {
                dataList.add(mPreloadObject);
            }
        }

        //do not stack page if null
        //user has scrolled to a point where original requesting paging block has been removed
        //this is caused by scrolling quickly where a more recent paging block has replaced an older...
        //one before its results could be stacked
        if (pagingBlock != null) {
            //set data lists
            pagingBlock.setDataListByPage(pageNumber, dataList);

        } else {
            return;
        }

        //if we're moving down a block
        if (stackOperation == GO_DOWN_ONE_BLOCK) {
            addItemsIntoAdapter(pagingBlock, pageNumber, dataList);

            //if we're moving up a block
        } else if (stackOperation == GO_UP_ONE_BLOCK){
            addItemsIntoAdapter(pagingBlock, pageNumber, dataList);
        }

        mIsIdle = true;
    }

    private void addItemsIntoAdapter(PagingBlock pagingBlock, int pageNumber, List dataList) {
        int pagingBlockIndex = mPagingBlockMap.indexOfValue(pagingBlock);

        //get index of the paging block's first item its the first page
        int blockStartingIndex = pagingBlockIndex
                * mPagingBlockTemplate.pageCapacity
                * mPagingBlockTemplate.blockPageCapacity;

        //use page number to find the index the page relative to existing pages currently in the block
        int pageIndex = pageNumber - pagingBlock.getFirstPageInBlock();
        //define first adapter position loop should begin.
        int firstInsertPosition = blockStartingIndex + (pageIndex * mPagingBlockTemplate.pageCapacity);

        for (int index = 0; index < dataList.size(); index++) {
            //increase value with each iteration through zero index
            int currentInsertPosition = firstInsertPosition + index;

            //catch exception caused by queries that get sent after adapter has already been
            //cleared during configuration changes
            try {
                //replace preload object and update adapter
                mStackMethods.getAdapterData().remove(currentInsertPosition);
                mStackMethods.getAdapterData().add(currentInsertPosition, dataList.get(index));

            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
            }
        }

        mAdapter.notifyItemRangeChanged(firstInsertPosition, dataList.size());

        //TODO add method that remove extra data BEFORE items are stacked to avoid late clean up
        //if incoming data < page capacity, remove extraneous empty data
        if (dataList.size() < mPagingBlockTemplate.pageCapacity) {
            int correctionDifference = mPagingBlockTemplate.pageCapacity - dataList.size();

            for (int i = 0; i < correctionDifference; i++) {
                try {
                    mStackMethods.getAdapterData().remove(mStackMethods.getAdapterData().size() - 1);

                } catch (IndexOutOfBoundsException e) {
                    e.printStackTrace();
                }

            }

            mAdapter.notifyItemRangeRemoved(correctionDifference - 1, correctionDifference);
        }

        //TODO data still in paging data now useless now that its been added to adapter.
    }

    private void removeTopBlock() {
        //stack is not idle
        mIsIdle = false;

        int firstKey = mPagingBlockMap.keyAt(0);
        int listSize = mPagingBlockMap.get(firstKey).getFullDataCount();

        //loop through length of block
        for (int index = 0; index < listSize; index++) {
            //remove top item in adapter
            mStackMethods.getAdapterData().remove(0);
            //notify change
            mAdapter.notifyItemRemoved(0);
        }

        mPagingBlockMap.remove(firstKey);

        //stack is idle
        mIsIdle = true;
    }

    private void removeBottomBlock() {
        //stack is not idle
        mIsIdle = false;

        int lastKey = mPagingBlockMap.keyAt(mPagingBlockMap.size() - 1);
        int listSize = mPagingBlockMap.get(lastKey).getFullDataCount();

        //loop through length of block
        for (int index = 0; index < listSize; index++) {
            //remove bottom item in adapter
            mStackMethods.getAdapterData().remove(mStackMethods.getAdapterData().size() - 1);
            //notify change
            mAdapter.notifyItemRemoved(mStackMethods.getAdapterData().size() - 1);
        }

        mPagingBlockMap.remove(lastKey);

        //stack is idle
        mIsIdle = true;
    }

    private void addTopBlock() {
        //stack is not idle
        mIsIdle = false;

        int firstKey = mPagingBlockMap.keyAt(0);
        int newKey = firstKey - 1;

        loadPreviousBlock(newKey);
    }

    private void loadPreviousBlock(int blockNumber) {
        //initialize paging block
        PagingBlock pagingBlock =
                new PagingBlock(getFirstPage(), blockNumber, mPagingBlockTemplate.blockPageCapacity);

        //add block to list
        mPagingBlockMap.put(blockNumber, pagingBlock);

        //define first targetPage
        int targetPage = pagingBlock.getFirstPageInBlock();

        //add placeholder objects till real stacking begins
        for (int i = 0; i < mPagingBlockTemplate.getBlockPageCapacity(); i++) {
            preStackPageBackWards();
        }

        //iterate through block page capacity
        for (int i = 0; i < mPagingBlockTemplate.getBlockPageCapacity(); i++) {
            //fetch page data
            mPagingBlockTemplate.createPageLoader.onPageStartReached(blockNumber, targetPage);

            //increase targetPage value
            targetPage += 1;
        }
    }

    private void preStackPageBackWards() {
        for (int i = mPagingBlockTemplate.pageCapacity - 1; i >= 0; i--) {
            //add item to front
            mStackMethods.getAdapterData().add(0, mPreloadObject);

        }

        mAdapter.notifyItemRangeInserted(0, mPagingBlockTemplate.pageCapacity);
    }

    private void addBottomBlock() {
        //stack is not idle
        mIsIdle = false;

        int lastKey = mPagingBlockMap.keyAt(mPagingBlockMap.size() - 1);
        int newKey = lastKey + 1;

        loadNextBlock(newKey);
    }

    private void loadNextBlock(int blockNumber) {
        //initialize paging block
        PagingBlock pagingBlock =
                new PagingBlock(getFirstPage(), blockNumber, mPagingBlockTemplate.blockPageCapacity);

        //add block to list
        mPagingBlockMap.put(blockNumber, pagingBlock);

        //define first targetPage
        int targetPage = pagingBlock.getFirstPageInBlock();

        //add placeholder objects till real stacking begins
        for (int i = 0; i < mPagingBlockTemplate.getBlockPageCapacity(); i++) {
            preStackPageForwards();
        }

        //TODO if number of pages ahead is < getBlockPageCapacity then extra api queries are a wasted
        //iterate through block page capacity
        for (int i = 0; i < mPagingBlockTemplate.getBlockPageCapacity(); i++) {
            //fetch page data
            mPagingBlockTemplate.createPageLoader.onPageEndReached(blockNumber, targetPage);

            //increase targetPage value
            targetPage += 1;
        }
    }

    private void preStackPageForwards() {
        for (int i = 0; i < mPagingBlockTemplate.pageCapacity; i++) {
            //add item to end
            mStackMethods.getAdapterData().add(mPreloadObject);
        }

        mAdapter.notifyItemRangeInserted(
                (mStackMethods.getAdapterData().size()-1) - (mPagingBlockTemplate.pageCapacity-1),
                mPagingBlockTemplate.pageCapacity);
    }

    private int getFirstPageInStack() throws IndexOutOfBoundsException {
        if (getPagingBlockMap().size() == 0) {
            throw new IndexOutOfBoundsException();
        }

        //get key of first block in stack
        int topPagingBlockKey = mPagingBlockMap.keyAt(0);
        //get top block using key
        PagingBlock topPagingBlock = mPagingBlockMap.get(topPagingBlockKey);

        return topPagingBlock.getFirstPageInBlock();
    }

    private int getLastPageInStack() throws IndexOutOfBoundsException {
        if (getPagingBlockMap().size() == 0) {
            throw new IndexOutOfBoundsException();
        }

        //get key of last block in stack
        int bottomPagingBlockKey = mPagingBlockMap.keyAt(mPagingBlockMap.size() - 1);
        //get bottom block using key
        PagingBlock bottomPagingBlock = mPagingBlockMap.get(bottomPagingBlockKey);

        return bottomPagingBlock.getLastPageInBlock();
    }

    public static class PagingBlockTemplate {
        final OnCreatePageLoader createPageLoader;
        private final int pageCapacity;
        private final int blockPageCapacity;

        public PagingBlockTemplate(OnCreatePageLoader createPageLoader, int pageCapacity,
                                   int blockPageCapacity) {
            this.createPageLoader = createPageLoader;
            this.pageCapacity = pageCapacity;
            this.blockPageCapacity = blockPageCapacity;
        }

        private int getBlockPageCapacity() {
            return blockPageCapacity;
        }

        public interface OnCreatePageLoader {
            void onPageEndReached(int blockNumber, int targetPage);
            void onPageStartReached(int blockNumber, int targetPage);
        }
    }

    private boolean atListEnd;
    private boolean atListStart;

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);

        @SuppressWarnings("ConstantConditions")
        //error caught in throw when invoking findLastCompletelyVisibleItemPosition()
        int lastShown = ((GridLayoutManager)recyclerView.getLayoutManager())
                .findLastVisibleItemPosition();

        //error caught in throw when invoking findFirstCompletelyVisibleItemPosition()
        int firstShownIndex = ((GridLayoutManager)recyclerView.getLayoutManager())
                .findFirstVisibleItemPosition();

        //isLastItem makes sure we are at the end of list
        boolean isLastItem = lastShown == mAdapter.getItemCount() - 1;
        //isFirstItem makes sure we are at the start of list
        boolean isFirstItem = firstShownIndex == 0;

        int availablePages = getTotalPages();

        //!emptyAdapter prevents unwanted page loads when clearing adapter data...
        // ...because lastItem is considered true
        boolean emptyAdapter = isAdapterEmpty();

        try {//catches error if paging block maps's size is 0
            boolean morePagesAhead = getLastPageInStack() < availablePages;

            //if at lastItem && if morePagesAhead && if adapter not empty
            atListEnd = isLastItem && morePagesAhead && !emptyAdapter;

        } catch (IndexOutOfBoundsException e) {
            atListEnd = false;
        }

        try {//catches error if paging block maps's size is 0
            boolean morePagesBehind = getFirstPageInStack() > getFirstPage();

            //if at firstItem && if morePagesBehind && if adapter not empty
            atListStart = isFirstItem && morePagesBehind && !emptyAdapter;

        } catch (IndexOutOfBoundsException e) {
            atListStart = false;
        }
    }

    @Override
    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        super.onScrollStateChanged(recyclerView, newState);
        if (atListEnd && newState == RecyclerView.SCROLL_STATE_IDLE) {
            if (mUsesInternet) {
                AppExecutors.getInstance().diskIO().execute(new Runnable() {
                    @Override
                    public void run() {
                        if (isOnline()) {
                            AppExecutors.getInstance().mainThread().execute(new Runnable() {
                                @Override
                                public void run() {
                                    if (mPagingBlockMap.size() == mBlockLimit) {
                                        removeTopBlock();
                                    }

                                    addBottomBlock();
                                }
                            });
                        }
                    }
                });

            } else {
                if (mPagingBlockMap.size() == mBlockLimit) {
                    removeTopBlock();
                }

                addBottomBlock();
            }

        } else if (atListStart && newState == RecyclerView.SCROLL_STATE_IDLE) {
            if (mUsesInternet) {
                AppExecutors.getInstance().diskIO().execute(new Runnable() {
                    @Override
                    public void run() {
                        if (isOnline()) {
                            AppExecutors.getInstance().mainThread().execute(new Runnable() {
                                @Override
                                public void run() {
                                    if (mPagingBlockMap.size() == mBlockLimit) {
                                        removeBottomBlock();
                                    }

                                    addTopBlock();
                                }
                            });
                        }
                    }
                });

            } else {
                if (mPagingBlockMap.size() == mBlockLimit) {
                    removeBottomBlock();
                }

                addTopBlock();
            }
        }
    }
}