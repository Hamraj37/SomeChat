package com.hamraj37.somechat.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.hamraj37.somechat.R;
import com.hamraj37.somechat.models.Highlight;

import java.util.List;

public class HighlightAdapter extends RecyclerView.Adapter<HighlightAdapter.HighlightViewHolder> {

    private List<Highlight> highlights;
    private OnHighlightClickListener listener;

    public interface OnHighlightClickListener {
        void onHighlightClick(Highlight highlight);
        void onHighlightLongClick(Highlight highlight);
    }

    public HighlightAdapter(List<Highlight> highlights, OnHighlightClickListener listener) {
        this.highlights = highlights;
        this.listener = listener;
    }

    @NonNull
    @Override
    public HighlightViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new HighlightViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_highlight, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull HighlightViewHolder holder, int position) {
        Highlight highlight = highlights.get(position);
        holder.title.setText(highlight.getTitle());
        
        Glide.with(holder.itemView.getContext())
                .load(highlight.getCoverUrl())
                .placeholder(R.mipmap.ic_launcher)
                .centerCrop()
                .into(holder.cover);

        holder.itemView.setOnClickListener(v -> listener.onHighlightClick(highlight));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onHighlightLongClick(highlight);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return highlights.size();
    }

    static class HighlightViewHolder extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title;

        public HighlightViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.highlight_cover);
            title = itemView.findViewById(R.id.highlight_title);
        }
    }
}
