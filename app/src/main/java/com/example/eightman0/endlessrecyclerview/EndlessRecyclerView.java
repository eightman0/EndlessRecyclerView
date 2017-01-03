package com.example.eightman0.endlessrecyclerview;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

public final class EndlessRecyclerView extends RecyclerView {

    private final Handler handler = new Handler();
    private final Runnable notifyDataSetChangedRunnable = new Runnable() {
        @Override
        public void run() {
            adapterWrapper.notifyDataSetChanged();
        }
    };

    private EndlessScrollListener endlessScrollListener;
    private LayoutManagerWrapper layoutManagerWrapper;
    private AdapterWrapper adapterWrapper;
    private View progressView;
    private boolean loading;
    private int threshold = 1;
    private int totalCount = 0;

    public EndlessRecyclerView(Context context) {
        this(context, null);
        createProgressView();
    }

    public EndlessRecyclerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
        createProgressView();
    }

    public EndlessRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        createProgressView();
    }

    @Override
    public void setAdapter(Adapter adapter) {
        adapterWrapper = new AdapterWrapper(adapter);
        super.setAdapter(adapterWrapper);
    }

    @Override
    public Adapter getAdapter() {
        return adapterWrapper.getAdapter();
    }

    @Override
    public void setLayoutManager(@Nullable LayoutManager layout) {
        layoutManagerWrapper = layout == null ? null : new LayoutManagerWrapper(layout);
        super.setLayoutManager(layout);
    }

    public void setLoadMoreListener(LoadMoreListener LoadMoreListener) {
        if (LoadMoreListener != null) {
            endlessScrollListener = new EndlessScrollListener(LoadMoreListener);
            endlessScrollListener.setThreshold(threshold);
            addOnScrollListener(endlessScrollListener);
        } else if (endlessScrollListener != null) {
            removeOnScrollListener(endlessScrollListener);
            endlessScrollListener = null;
        }
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
        if (endlessScrollListener != null) {
            endlessScrollListener.setThreshold(threshold);
        }
    }

    public void setProgressView(int layoutResId) {
        setProgressView(LayoutInflater
                .from(getContext())
                .inflate(layoutResId, this, false));
    }

    public void setProgressView(View view) {
        progressView = view;
    }
    public void setProgressColor(int color){
        ((ProgressBar)progressView).getIndeterminateDrawable().setColorFilter(ContextCompat.getColor(getContext(), color), PorterDuff.Mode.SRC_IN);
    }

    public void stopLoading(boolean loading) {
        if (this.loading == loading) {
            return;
        }
        this.loading = loading;
        notifyDataSetChanged();
    }

    public boolean isRefreshing() {
        return loading;
    }

    private void notifyDataSetChanged() {
        if (isComputingLayout()) {
            handler.post(notifyDataSetChangedRunnable);
        } else {
            adapterWrapper.notifyDataSetChanged();
        }
    }

    private static final class LayoutManagerWrapper {

        @NonNull
        final LayoutManager layoutManager;

        @NonNull
        private final LayoutManagerResolver resolver;

        LayoutManagerWrapper(@NonNull LayoutManager layoutManager) {
            this.layoutManager = layoutManager;
            this.resolver = getResolver(layoutManager);
        }

        @NonNull
        private static LayoutManagerResolver getResolver(@NonNull LayoutManager layoutManager) {
            if (layoutManager instanceof LinearLayoutManager) {
                return new LayoutManagerResolver() {
                    @Override
                    public int findLastVisibleItemPosition(@NonNull LayoutManager layoutManager) {
                        return ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition();
                    }
                };
            } else if (layoutManager instanceof StaggeredGridLayoutManager) {
                return new LayoutManagerResolver() {
                    @Override
                    public int findLastVisibleItemPosition(@NonNull LayoutManager layoutManager) {
                        int[] lastVisibleItemPositions =
                                ((StaggeredGridLayoutManager) layoutManager)
                                        .findLastVisibleItemPositions(null);
                        int lastVisibleItemPosition = lastVisibleItemPositions[0];
                        for (int i = 1; i < lastVisibleItemPositions.length; ++i) {
                            if (lastVisibleItemPosition < lastVisibleItemPositions[i]) {
                                lastVisibleItemPosition = lastVisibleItemPositions[i];
                            }
                        }
                        return lastVisibleItemPosition;
                    }
                };
            } else {
                throw new IllegalArgumentException("unsupported layout manager: " + layoutManager);
            }
        }

        int findLastVisibleItemPosition() {
            return resolver.findLastVisibleItemPosition(layoutManager);
        }

        private interface LayoutManagerResolver {
            int findLastVisibleItemPosition(@NonNull LayoutManager layoutManager);
        }
    }

    private final class EndlessScrollListener extends OnScrollListener {

        private final LoadMoreListener loadMoreListener;

        private int threshold = 1;

        EndlessScrollListener(LoadMoreListener loadMoreListener) {
            if (loadMoreListener == null) {
                throw new NullPointerException("LoadMoreListener is null");
            }
            this.loadMoreListener = loadMoreListener;
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            int lastVisibleItemPosition = layoutManagerWrapper.findLastVisibleItemPosition();
            int lastItemPosition = getAdapter().getItemCount();

            if ((loadMoreListener.shouldLoad()) &&
                    (lastItemPosition - lastVisibleItemPosition <= threshold) &&
                    (totalCount != lastItemPosition) &&
                    (lastItemPosition % threshold == 0)) {
                stopLoading(true);
                loadMoreListener.loadNextPage();
                totalCount = lastItemPosition;
            }
        }

        void setThreshold(int threshold) {
            if (threshold <= 0) {
                throw new IllegalArgumentException("illegal threshold: " + threshold);
            }
            this.threshold = threshold;
        }
    }

    private final class AdapterWrapper extends Adapter<ViewHolder> {

        private static final int PROGRESS_VIEW_TYPE = -1;

        private final Adapter<ViewHolder> adapter;

        private ProgressViewHolder progressViewHolder;

        AdapterWrapper(Adapter<ViewHolder> adapter) {
            if (adapter == null) {
                throw new NullPointerException("adapter is null");
            }
            this.adapter = adapter;
            setHasStableIds(adapter.hasStableIds());
        }

        @Override
        public int getItemCount() {
            return adapter.getItemCount() + (loading && progressView != null ? 1 : 0);
        }

        @Override
        public long getItemId(int position) {
            return position == adapter.getItemCount() ? NO_ID : adapter.getItemId(position);
        }

        @Override
        public int getItemViewType(int position) {
            return loading & position == adapter.getItemCount() ? PROGRESS_VIEW_TYPE :
                    adapter.getItemViewType(position);
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
            adapter.onAttachedToRecyclerView(recyclerView);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            if (position < adapter.getItemCount()) {
                adapter.onBindViewHolder(holder, position);
            }
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return viewType == PROGRESS_VIEW_TYPE ? progressViewHolder = new ProgressViewHolder() :
                    adapter.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
            super.onDetachedFromRecyclerView(recyclerView);
            adapter.onDetachedFromRecyclerView(recyclerView);
        }

        @Override
        public boolean onFailedToRecycleView(ViewHolder holder) {
            return holder == progressViewHolder || adapter.onFailedToRecycleView(holder);
        }

        @Override
        public void onViewAttachedToWindow(ViewHolder holder) {
            if (holder == progressViewHolder) {
                return;
            }
            adapter.onViewAttachedToWindow(holder);
        }

        @Override
        public void onViewDetachedFromWindow(ViewHolder holder) {
            if (holder == progressViewHolder) {
                return;
            }
            adapter.onViewDetachedFromWindow(holder);
        }

        @Override
        public void onViewRecycled(ViewHolder holder) {
            if (holder == progressViewHolder) {
                return;
            }
            adapter.onViewRecycled(holder);
        }

        @Override
        public void registerAdapterDataObserver(AdapterDataObserver observer) {
            super.registerAdapterDataObserver(observer);
            adapter.registerAdapterDataObserver(observer);
        }

        @Override
        public void unregisterAdapterDataObserver(AdapterDataObserver observer) {
            super.unregisterAdapterDataObserver(observer);
            adapter.unregisterAdapterDataObserver(observer);
        }

        Adapter<ViewHolder> getAdapter() {
            return adapter;
        }

        private final class ProgressViewHolder extends ViewHolder {
            ProgressViewHolder() {
                super(progressView);
            }
        }
    }

    public interface LoadMoreListener {
        boolean shouldLoad();

        void loadNextPage();
    }

    private void createProgressView() {
        ProgressBar progressBar = new ProgressBar(getContext());
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, convertDpToPixel(30));
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        params.bottomMargin = convertDpToPixel(10);
        progressBar.setLayoutParams(params);
        progressView = progressBar;
    }
    public int convertDpToPixel(float dp) {
        Context context = getContext();
        if (context == null) {
            return 30; // context should never be a null
        }
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float px = dp * ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
        return (int) px;
    }
}
