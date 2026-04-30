package com.example.zerovelocity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.ViewHolder> {

    private List<FeedItem> items;

    public FeedAdapter(List<FeedItem> items) {
        this.items = items;
    }

    public void update(List<FeedItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_feed, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FeedItem item = items.get(position);
        holder.text.setText(item.text);

        if (item.rank >= 1 && item.rank <= 3) {
            holder.rank.setVisibility(View.VISIBLE);
            holder.rank.setText(getRankLabel(item.rank));
        } else {
            holder.rank.setVisibility(View.GONE);
        }

        int backgroundColor = R.color.auth_input_fill;
        int strokeColor = R.color.auth_card_stroke;

        if (item.rank == 1) {
            backgroundColor = R.color.feed_gold;
            strokeColor = R.color.feed_gold_stroke;
        } else if (item.rank == 2) {
            backgroundColor = R.color.feed_silver;
            strokeColor = R.color.feed_silver_stroke;
        } else if (item.rank == 3) {
            backgroundColor = R.color.feed_bronze;
            strokeColor = R.color.feed_bronze_stroke;
        }

        holder.card.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), backgroundColor));
        holder.card.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(), strokeColor));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String getRankLabel(int rank) {
        if (rank == 1) {
            return "1st";
        } else if (rank == 2) {
            return "2nd";
        } else if (rank == 3) {
            return "3rd";
        }
        return "";
    }

    static class FeedItem {
        String text;
        int rank;

        FeedItem(String text, int rank) {
            this.text = text;
            this.rank = rank;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView rank;
        TextView text;

        ViewHolder(View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_feed_item);
            rank = itemView.findViewById(R.id.tv_feed_rank);
            text = itemView.findViewById(R.id.tv_feed_item);
        }
    }
}
