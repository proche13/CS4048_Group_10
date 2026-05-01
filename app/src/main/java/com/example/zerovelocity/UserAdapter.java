package com.example.zerovelocity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {

    public interface OnActionClick {
        void onAction(UserItem user);
    }

    private final List<UserItem> items;
    private final String buttonLabel;
    private final OnActionClick callback;

    public UserAdapter(List<UserItem> items, String buttonLabel, OnActionClick callback) {
        this.items = items;
        this.buttonLabel = buttonLabel;
        this.callback = callback;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserItem user = items.get(position);
        holder.tvName.setText(user.displayName);
        holder.btnAction.setText(user.pending ? "Pending" : buttonLabel);
        holder.btnAction.setEnabled(!user.pending);
        holder.btnAction.setOnClickListener(user.pending ? null : v -> callback.onAction(user));
        holder.btnAction.setBackgroundTintList(ContextCompat.getColorStateList(
                holder.itemView.getContext(),
                user.pending ? R.color.auth_text_secondary : R.color.auth_button));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final Button btnAction;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_user_name);
            btnAction = itemView.findViewById(R.id.btn_action);
        }
    }
}
