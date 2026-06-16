package com.example.zerovelocity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    static class Entry {
        final int rank;
        final String displayName;
        final float totalUnits;

        Entry(int rank, String displayName, float totalUnits) {
            this.rank = rank;
            this.displayName = displayName;
            this.totalUnits = totalUnits;
        }
    }

    private final List<Entry> items;
    private String quantityEmoji = "🍺";

    public LeaderboardAdapter(List<Entry> items) {
        this.items = items;
    }

    public void setQuantityEmoji(String quantityEmoji) {
        this.quantityEmoji = quantityEmoji;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Entry entry = items.get(position);
        holder.tvRank.setText(String.valueOf(entry.rank));
        holder.tvName.setText(entry.displayName);
        holder.tvUnits.setText(formatQuantity(entry.totalUnits, quantityEmoji));
        if (!entry.displayName.isEmpty()) {
            holder.tvInitial.setText(
                    String.valueOf(entry.displayName.charAt(0)).toUpperCase(Locale.getDefault()));
        }
    }

    static String formatQuantity(float totalUnits, String emoji) {
        return String.format(Locale.getDefault(), "%d %s", Math.round(totalUnits), emoji);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvRank;
        final TextView tvName;
        final TextView tvUnits;
        final TextView tvInitial;

        ViewHolder(View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tv_rank);
            tvName = itemView.findViewById(R.id.tv_name);
            tvUnits = itemView.findViewById(R.id.tv_units);
            tvInitial = itemView.findViewById(R.id.tv_initial);
        }
    }
}
