package com.example.zerovelocity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.ViewHolder> {

    private List<FeedItem> items;
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> imageCache = new ConcurrentHashMap<>();
    private final Set<String> expandedDescriptionKeys = ConcurrentHashMap.newKeySet();

    public FeedAdapter(List<FeedItem> items) {
        this.items = items;
    }

    public void update(List<FeedItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public void shutdown() {
        imageExecutor.shutdownNow();
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
        holder.title.setText(item.title);
        holder.details.setText(item.details);
        loadImage(holder.profileImage, item.profilePictureUrl, true);

        if (TextUtils.isEmpty(item.logImageUrl)) {
            holder.logImage.setVisibility(View.GONE);
            holder.logImage.setImageDrawable(null);
            holder.logImage.setTag(null);
        } else {
            holder.logImage.setVisibility(View.VISIBLE);
            loadImage(holder.logImage, item.logImageUrl, false);
        }

        bindDescription(holder, item);
        bindRankBadge(holder, item.rank);

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

    private void bindRankBadge(ViewHolder holder, int rank) {
        if (rank >= 1 && rank <= 3) {
            holder.rank.setVisibility(View.VISIBLE);
            holder.rank.setText(getRankLabel(rank));
        } else {
            holder.rank.setVisibility(View.GONE);
        }
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

    private void bindDescription(ViewHolder holder, FeedItem item) {
        if (TextUtils.isEmpty(item.description)) {
            holder.descriptionRow.setVisibility(View.GONE);
            holder.description.setText("");
            holder.descriptionToggle.setVisibility(View.GONE);
            holder.descriptionToggle.setOnClickListener(null);
            return;
        }

        String fullDescription = item.description.trim();
        String[] words = fullDescription.split("\\s+");
        boolean canExpand = words.length > 6;
        boolean expanded = expandedDescriptionKeys.contains(item.getExpansionKey());

        holder.descriptionRow.setVisibility(View.VISIBLE);
        holder.description.setText(canExpand && !expanded
                ? getFirstWords(words, 6)
                : fullDescription);

        if (canExpand) {
            holder.descriptionToggle.setVisibility(View.VISIBLE);
            holder.descriptionToggle.setText(expanded ? "Less" : "More");
            holder.descriptionToggle.setOnClickListener(v -> {
                String expansionKey = item.getExpansionKey();
                if (expandedDescriptionKeys.contains(expansionKey)) {
                    expandedDescriptionKeys.remove(expansionKey);
                } else {
                    expandedDescriptionKeys.add(expansionKey);
                }
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(adapterPosition);
                }
            });
        } else {
            holder.descriptionToggle.setVisibility(View.GONE);
            holder.descriptionToggle.setOnClickListener(null);
        }
    }

    private String getFirstWords(String[] words, int wordCount) {
        StringBuilder preview = new StringBuilder();
        int limit = Math.min(words.length, wordCount);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                preview.append(" ");
            }
            preview.append(words[i]);
        }
        return preview.toString();
    }

    private void loadImage(ImageView imageView, String imageUrl, boolean circular) {
        imageView.setTag(imageUrl);

        if (TextUtils.isEmpty(imageUrl)) {
            imageView.setImageResource(R.mipmap.ic_launcher_round);
            return;
        }

        Bitmap cached = imageCache.get(imageUrl);
        if (cached != null) {
            setBitmap(imageView, cached, circular);
            return;
        }

        imageView.setImageResource(R.mipmap.ic_launcher_round);
        imageExecutor.execute(() -> {
            try (InputStream input = new URL(imageUrl).openStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap == null) {
                    return;
                }

                imageCache.put(imageUrl, bitmap);

                mainHandler.post(() -> {
                    Object tag = imageView.getTag();
                    if (imageUrl.equals(tag)) {
                        setBitmap(imageView, bitmap, circular);
                    }
                });
            } catch (Exception ignored) {
            }
        });
    }

    private void setBitmap(ImageView imageView, Bitmap bitmap, boolean circular) {
        if (circular) {
            RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(
                    imageView.getResources(), bitmap);
            drawable.setCircular(true);
            imageView.setImageDrawable(drawable);
        } else {
            imageView.setImageBitmap(bitmap);
        }
    }

    static class FeedItem {
        String title;
        String details;
        String description;
        int rank;
        String profilePictureUrl;
        String logImageUrl;

        FeedItem(String title, String details, String description,
                 int rank, String profilePictureUrl, String logImageUrl) {
            this.title = title;
            this.details = details;
            this.description = description;
            this.rank = rank;
            this.profilePictureUrl = profilePictureUrl;
            this.logImageUrl = logImageUrl;
        }

        String getExpansionKey() {
            if (!TextUtils.isEmpty(logImageUrl)) {
                return logImageUrl;
            }
            return title + "|" + details + "|" + description;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView profileImage;
        ImageView logImage;
        LinearLayout descriptionRow;
        TextView rank;
        TextView title;
        TextView details;
        TextView description;
        TextView descriptionToggle;

        ViewHolder(View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_feed_item);
            profileImage = itemView.findViewById(R.id.iv_feed_profile);
            logImage = itemView.findViewById(R.id.iv_feed_log_image);
            descriptionRow = itemView.findViewById(R.id.ll_feed_description);
            rank = itemView.findViewById(R.id.tv_feed_rank);
            title = itemView.findViewById(R.id.tv_feed_title);
            details = itemView.findViewById(R.id.tv_feed_details);
            description = itemView.findViewById(R.id.tv_feed_description);
            descriptionToggle = itemView.findViewById(R.id.tv_feed_description_toggle);
        }
    }
}
