package com.sean.android.example.ui.main;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.sean.android.example.R;
import com.sean.android.example.base.view.OnItemClickListener;
import com.sean.android.example.ui.main.viewmodel.GalleryItemViewModel;
import com.sean.android.example.ui.main.viewmodel.ViewBinder;

/**
 * Created by sean on 2017. 3. 11..
 */

public class GalleryViewHolder extends RecyclerView.ViewHolder implements ViewBinder<GalleryItemViewModel> {


    private OnItemClickListener onItemClickListener;

    private TextView titleTextView;
    private ImageView galleryImageView;

    public GalleryViewHolder(View itemView, OnItemClickListener onItemClickListener) {
        super(itemView);

        titleTextView = (TextView) itemView.findViewById(R.id.item_title_textView);
        galleryImageView = (ImageView) itemView.findViewById(R.id.item_imageView);

        this.onItemClickListener = onItemClickListener;
    }

    @Override
    public void onBind(final GalleryItemViewModel galleryItemViewModel) {
        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onItemClickListener != null && getAdapterPosition() != RecyclerView.NO_POSITION)
                    onItemClickListener.onClickItem(getAdapterPosition());
            }
        });

        titleTextView.setText(galleryItemViewModel.getTitle());
        //TODO Image load

    }
}