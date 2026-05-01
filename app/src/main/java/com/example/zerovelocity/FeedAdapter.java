package com.example.zerovelocity;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.recyclerview.widget.RecyclerView;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.ViewHolder> {

    private static final String[] REACTION_EMOJIS = {"🍺", "🔥", "😂", "💀", "🤮"};

    public interface OnInteractionListener {
        void onCheerToggle(String postId, boolean currentlyCheered);
        void onEmojiReact(String postId, String emoji, String currentMyReaction);
        void onCommentSubmit(String postId, String text);
    }

    private List<FeedItem> items;
    private final OnInteractionListener listener;
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> imageCache = new ConcurrentHashMap<>();
    private final Set<String> expandedDescriptionKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> expandedCommentsKeys = ConcurrentHashMap.newKeySet();

    public FeedAdapter(List<FeedItem> items, OnInteractionListener listener) {
        this.items = items;
        this.listener = listener;
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
        bindCheers(holder, item);
        bindReactions(holder, item);
        bindComments(holder, item);

        // Tint root background for top-ranked posts
        int bgColor;
        if (item.rank == 1) bgColor = R.color.feed_gold;
        else if (item.rank == 2) bgColor = R.color.feed_silver;
        else if (item.rank == 3) bgColor = R.color.feed_bronze;
        else bgColor = R.color.auth_surface;
        holder.root.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), bgColor));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void bindRankBadge(ViewHolder holder, int rank) {
        if (rank == 1) {
            holder.rank.setVisibility(View.VISIBLE);
            holder.rank.setText("🥇");
        } else if (rank == 2) {
            holder.rank.setVisibility(View.VISIBLE);
            holder.rank.setText("🥈");
        } else if (rank == 3) {
            holder.rank.setVisibility(View.VISIBLE);
            holder.rank.setText("🥉");
        } else {
            holder.rank.setVisibility(View.GONE);
        }
    }

    private void bindCheers(ViewHolder holder, FeedItem item) {
        holder.cheers.setText("🥂 " + item.cheerCount);
        int color = item.cheeredByMe
                ? ContextCompat.getColor(holder.itemView.getContext(), R.color.auth_button)
                : ContextCompat.getColor(holder.itemView.getContext(), R.color.auth_text_secondary);
        holder.cheers.setTextColor(color);
        holder.cheers.setOnClickListener(v -> {
            if (listener != null) listener.onCheerToggle(item.postId, item.cheeredByMe);
        });
    }

    private void bindReactions(ViewHolder holder, FeedItem item) {
        String reactLabel = TextUtils.isEmpty(item.myReaction) ? "😄" : item.myReaction;
        holder.react.setText(reactLabel);
        holder.react.setOnClickListener(v -> showEmojiPicker(holder, item));

        if (item.reactionCounts == null || item.reactionCounts.isEmpty()) {
            holder.reactionsSummary.setVisibility(View.GONE);
        } else {
            holder.reactionsSummary.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> entry : item.reactionCounts.entrySet()) {
                sb.append(entry.getKey()).append(" ").append(entry.getValue()).append("  ");
            }
            holder.reactionsSummary.setText(sb.toString().trim());
        }
    }

    private void showEmojiPicker(ViewHolder holder, FeedItem item) {
        new AlertDialog.Builder(holder.itemView.getContext())
                .setTitle("React")
                .setItems(REACTION_EMOJIS, (dialog, which) -> {
                    if (listener != null) {
                        listener.onEmojiReact(item.postId, REACTION_EMOJIS[which], item.myReaction);
                    }
                })
                .show();
    }

    private void bindComments(ViewHolder holder, FeedItem item) {
        holder.commentCount.setText("💬 " + item.totalCommentCount);
        holder.commentInput.setText("");

        Runnable submitAction = () -> {
            String text = holder.commentInput.getText().toString().trim();
            if (!TextUtils.isEmpty(text) && listener != null) {
                listener.onCommentSubmit(item.postId, text);
                holder.commentInput.setText("");
            }
        };
        holder.commentSend.setOnClickListener(v -> submitAction.run());
        holder.commentInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                submitAction.run();
                return true;
            }
            return false;
        });

        if (item.comments == null || item.comments.isEmpty()) {
            holder.commentsPreview.setVisibility(View.GONE);
            return;
        }

        holder.commentsPreview.setVisibility(View.VISIBLE);
        boolean expanded = expandedCommentsKeys.contains(item.postId);
        int total = item.comments.size();

        // "View all" / "Hide" toggle
        if (total > 2 && !expanded) {
            holder.viewAllComments.setVisibility(View.VISIBLE);
            holder.viewAllComments.setText("View all " + total + " comments");
            holder.viewAllComments.setOnClickListener(v -> {
                expandedCommentsKeys.add(item.postId);
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
            });
        } else if (expanded && total > 2) {
            holder.viewAllComments.setVisibility(View.VISIBLE);
            holder.viewAllComments.setText("Hide comments");
            holder.viewAllComments.setOnClickListener(v -> {
                expandedCommentsKeys.remove(item.postId);
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
            });
        } else {
            holder.viewAllComments.setVisibility(View.GONE);
        }

        if (expanded) {
            // Show all comments stacked in comment1, hide comment2
            SpannableStringBuilder sb = new SpannableStringBuilder();
            for (int i = 0; i < total; i++) {
                if (i > 0) sb.append("\n");
                appendComment(sb, item.comments.get(i)[0], item.comments.get(i)[1]);
            }
            holder.comment1.setText(sb);
            holder.comment1.setVisibility(View.VISIBLE);
            holder.comment2.setVisibility(View.GONE);
        } else {
            // Show last 2 comments
            int start = Math.max(0, total - 2);
            String[] c1 = item.comments.get(start);
            holder.comment1.setText(buildComment(c1[0], c1[1]));
            holder.comment1.setVisibility(View.VISIBLE);

            if (total >= 2) {
                String[] c2 = item.comments.get(start + 1);
                holder.comment2.setText(buildComment(c2[0], c2[1]));
                holder.comment2.setVisibility(View.VISIBLE);
            } else {
                holder.comment2.setVisibility(View.GONE);
            }
        }
    }

    private CharSequence buildComment(String username, String text) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendComment(sb, username, text);
        return sb;
    }

    private void appendComment(SpannableStringBuilder sb, String username, String text) {
        int start = sb.length();
        sb.append(username);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(),
                SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("  ").append(text);
    }

    private void bindDescription(ViewHolder holder, FeedItem item) {
        if (TextUtils.isEmpty(item.description)) {
            holder.descriptionRow.setVisibility(View.GONE);
            holder.description.setText("");
            holder.descriptionToggle.setVisibility(View.GONE);
            holder.descriptionToggle.setOnClickListener(null);
            return;
        }

        String full = item.description.trim();
        String[] words = full.split("\\s+");
        boolean canExpand = words.length > 6;
        boolean expanded = expandedDescriptionKeys.contains(item.getExpansionKey());

        holder.descriptionRow.setVisibility(View.VISIBLE);
        holder.description.setText(canExpand && !expanded ? getFirstWords(words, 6) + "…" : full);

        if (canExpand) {
            holder.descriptionToggle.setVisibility(View.VISIBLE);
            holder.descriptionToggle.setText(expanded ? "Less" : "More");
            holder.descriptionToggle.setOnClickListener(v -> {
                String key = item.getExpansionKey();
                if (expandedDescriptionKeys.contains(key)) expandedDescriptionKeys.remove(key);
                else expandedDescriptionKeys.add(key);
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
            });
        } else {
            holder.descriptionToggle.setVisibility(View.GONE);
            holder.descriptionToggle.setOnClickListener(null);
        }
    }

    private String getFirstWords(String[] words, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(words.length, count); i++) {
            if (i > 0) sb.append(" ");
            sb.append(words[i]);
        }
        return sb.toString();
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
                if (bitmap == null) return;
                imageCache.put(imageUrl, bitmap);
                mainHandler.post(() -> {
                    if (imageUrl.equals(imageView.getTag())) setBitmap(imageView, bitmap, circular);
                });
            } catch (Exception ignored) {
            }
        });
    }

    private void setBitmap(ImageView imageView, Bitmap bitmap, boolean circular) {
        if (circular) {
            RoundedBitmapDrawable d = RoundedBitmapDrawableFactory.create(imageView.getResources(), bitmap);
            d.setCircular(true);
            imageView.setImageDrawable(d);
        } else {
            imageView.setImageBitmap(bitmap);
        }
    }

    static class FeedItem {
        String postId;
        String title;
        String details;
        String description;
        int rank;
        String profilePictureUrl;
        String logImageUrl;
        int cheerCount;
        boolean cheeredByMe;
        Map<String, Integer> reactionCounts;
        String myReaction;
        List<String[]> comments;
        int totalCommentCount;

        FeedItem(String postId, String title, String details, String description,
                 int rank, String profilePictureUrl, String logImageUrl,
                 int cheerCount, boolean cheeredByMe,
                 Map<String, Integer> reactionCounts, String myReaction,
                 List<String[]> comments, int totalCommentCount) {
            this.postId = postId;
            this.title = title;
            this.details = details;
            this.description = description;
            this.rank = rank;
            this.profilePictureUrl = profilePictureUrl;
            this.logImageUrl = logImageUrl;
            this.cheerCount = cheerCount;
            this.cheeredByMe = cheeredByMe;
            this.reactionCounts = reactionCounts;
            this.myReaction = myReaction;
            this.comments = comments;
            this.totalCommentCount = totalCommentCount;
        }

        String getExpansionKey() {
            return TextUtils.isEmpty(postId) ? title + "|" + details : postId;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout root;
        ImageView profileImage;
        ImageView logImage;
        TextView title;
        TextView details;
        TextView rank;
        LinearLayout descriptionRow;
        TextView description;
        TextView descriptionToggle;
        TextView cheers;
        TextView react;
        TextView reactionsSummary;
        TextView commentCount;
        LinearLayout commentsPreview;
        TextView viewAllComments;
        TextView comment1;
        TextView comment2;
        EditText commentInput;
        TextView commentSend;

        ViewHolder(View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.ll_feed_root);
            profileImage = itemView.findViewById(R.id.iv_feed_profile);
            logImage = itemView.findViewById(R.id.iv_feed_log_image);
            title = itemView.findViewById(R.id.tv_feed_title);
            details = itemView.findViewById(R.id.tv_feed_details);
            rank = itemView.findViewById(R.id.tv_feed_rank);
            descriptionRow = itemView.findViewById(R.id.ll_feed_description);
            description = itemView.findViewById(R.id.tv_feed_description);
            descriptionToggle = itemView.findViewById(R.id.tv_feed_description_toggle);
            cheers = itemView.findViewById(R.id.tv_cheers);
            react = itemView.findViewById(R.id.tv_react);
            reactionsSummary = itemView.findViewById(R.id.tv_reactions_summary);
            commentCount = itemView.findViewById(R.id.tv_comment_count);
            commentsPreview = itemView.findViewById(R.id.ll_comments_preview);
            viewAllComments = itemView.findViewById(R.id.tv_view_all_comments);
            comment1 = itemView.findViewById(R.id.tv_comment_1);
            comment2 = itemView.findViewById(R.id.tv_comment_2);
            commentInput = itemView.findViewById(R.id.et_comment_input);
            commentSend = itemView.findViewById(R.id.tv_comment_send);
        }
    }
}
