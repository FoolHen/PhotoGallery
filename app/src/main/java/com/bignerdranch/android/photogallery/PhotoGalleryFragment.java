package com.bignerdranch.android.photogallery;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Fulgen on 15/07/2016.
 */
public class PhotoGalleryFragment extends Fragment{

    private static final String TAG = "PhotoGalleryFragment";
    private static final int COLUMN_WIDTH = 300;
    private static final int BUFFER_SIZE = 10;

    private static RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;

    public int page = 1;
    public static PhotoGalleryFragment newInstance(){
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
        updateItems();

        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler, getContext());
        mThumbnailDownloader.setThumbnailDownloadListener(
                new ThumbnailDownloader.ThumbnailDownloadListener<PhotoHolder>() {
                    @Override
                    public void onThumbnailDownloaded(PhotoHolder photoHolder, Bitmap bitmap) {
                        Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                        photoHolder.bindDrawable(drawable);

                    }
                }
        );
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG,"Background thread started");
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery,container,false);


        mPhotoRecyclerView = (RecyclerView) v.findViewById(R.id.fragment_photo_gallery_recycler_view);
        mPhotoRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(),1));
        mPhotoRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int width = mPhotoRecyclerView.getWidth() / COLUMN_WIDTH;
                GridLayoutManager layoutManager = (GridLayoutManager) mPhotoRecyclerView.getLayoutManager();
                layoutManager.setSpanCount(width);

            }
        });

        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                GridLayoutManager gridLayoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                switch (newState){
                    case RecyclerView.SCROLL_STATE_IDLE:
                        int itemCount = recyclerView.getAdapter().getItemCount();
                        int lastPosition = gridLayoutManager.findLastCompletelyVisibleItemPosition();
                        int firstPosition = gridLayoutManager.findFirstCompletelyVisibleItemPosition();

                        preloadImages(firstPosition,lastPosition);

                        if ((itemCount - 1) == lastPosition){
                            Log.i(TAG,"itemcount -1 = lastPosition");
                            page++;
                            clearAll();
                            updateItems();
                            Toast.makeText(getActivity().getApplicationContext()
                                    ,"Loading page " + String.valueOf(page) + "...",Toast.LENGTH_LONG)
                                    .show();
                        }
                        else if (firstPosition == 0 && page > 1){
                            page--;
                            clearAll();
                            updateItems();
                            Toast.makeText(getActivity().getApplicationContext()
                                    ,"Loading page " + String.valueOf(page) + "...",Toast.LENGTH_LONG)
                                    .show();
                        }
                        break;
                    case RecyclerView.SCROLL_STATE_DRAGGING:
                        mThumbnailDownloader.clearDownloadQueue();
                        mThumbnailDownloader.clearPreloadQueue();
                        break;
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
            }
        });


        setupAdapter();
        return v;
    }

    private void preloadImages(int first, int last){

        int max = Math.min(last + BUFFER_SIZE, mItems.size()-1);
        int min = Math.max(first - BUFFER_SIZE, 0);
        for (int i = min; i <= max; i++){
            if (i >= first && i <= last){ //If the item is in the screen it doesn't download it
                continue;
            }
            Log.i(TAG,"Thumbnail preload request sent" );
            mThumbnailDownloader.preloadThumbnail(mItems.get(i).getUrl());
        }

    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clearAll();
    }

    private void clearAll(){
        mThumbnailDownloader.clearDownloadQueue();
        mThumbnailDownloader.clearPreloadQueue();
        mThumbnailDownloader.clearCache();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_photo_gallery, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(TAG, "QueryTextSubmit: " + query);
                QueryPreferences.setStoredQuery(getActivity(),query);
                updateItems();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d(TAG, "QueryTextChange: " + newText);
                return false;
            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.menu_item_clear:
                QueryPreferences.setStoredQuery(getActivity(),null);
                updateItems();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateItems(){
        String query = QueryPreferences.getStoredQuery(getActivity());
        new FetchItemsTask(query).execute();
    }

    private void setupAdapter(){
        if (isAdded()){
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    private class FetchItemsTask extends AsyncTask<Void,Void,List<GalleryItem>> {
        private String mQuery;

        public FetchItemsTask(String query){
            mQuery = query;
        }

        @Override
        protected List<GalleryItem> doInBackground(Void... params) {

            if (mQuery == null){
                return new FlickrFetchr().fetchRecentPhotos(page);
            } else {
                return new FlickrFetchr().searchPhotos(mQuery, page);
            }
        }

        @Override
        protected void onPostExecute(List<GalleryItem> items) {
            mItems = items;
            setupAdapter();
        }
    }
    private class PhotoHolder extends RecyclerView.ViewHolder{
        private ImageView mItemImageView;

        public PhotoHolder(View itemView) {
            super(itemView);
            mItemImageView = (ImageView) itemView
                    .findViewById(R.id.fragment_photo_gallery_image_view);
        }

        public void bindDrawable(Drawable drawable){
            mItemImageView.setImageDrawable(drawable);

        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder>{

        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.gallery_item, viewGroup,false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder photoHolder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            Bitmap bitmap = mThumbnailDownloader.getCachedImage(galleryItem.getUrl());

            if (bitmap == null){
                Drawable placeholder = getResources().getDrawable(R.drawable.loading_icon);
                photoHolder.bindDrawable(placeholder);
                mThumbnailDownloader.queueThumbnail(photoHolder, galleryItem.getUrl());
            }
            else {
                photoHolder.bindDrawable(new BitmapDrawable(getResources(), bitmap));
            }
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }
}
