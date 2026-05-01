package com.example.zerovelocity;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private final List<EventItem> events = new ArrayList<>();
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault());

    private String currentUid;

    public void setCurrentUid(String uid) {
        this.currentUid = uid;
    }

    public void setEvents(List<EventItem> newEvents) {
        events.clear();
        events.addAll(newEvents);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        EventItem event = events.get(position);
        holder.tvTitle.setText(event.title);
        holder.tvDate.setText(
                TIME_FORMAT.format(new Date(event.startTime))
                        + " → "
                        + TIME_FORMAT.format(new Date(event.endTime)));

        holder.btnInvite.setOnClickListener(v ->
                showInviteDialog(v.getContext(), event));
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    private void showInviteDialog(Context context, EventItem event) {
        if (currentUid == null) {
            Toast.makeText(context, "You must be logged in to invite friends.", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference dbRef = FirebaseRefs.root();

        dbRef.child("friends").child(currentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot friendsSnap) {
                        List<String> friendUids = new ArrayList<>();
                        for (DataSnapshot child : friendsSnap.getChildren()) {
                            friendUids.add(child.getKey());
                        }

                        if (friendUids.isEmpty()) {
                            Toast.makeText(context, "You have no friends to invite yet.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        fetchUsernamesAndShowDialog(context, event, friendUids, dbRef);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(context, "Could not load friends.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    private void fetchUsernamesAndShowDialog(Context context, EventItem event,
                                             List<String> friendUids,
                                             DatabaseReference dbRef) {
        String[] names = new String[friendUids.size()];
        boolean[] checked = new boolean[friendUids.size()];
        int[] remaining = {friendUids.size()};

        for (int i = 0; i < friendUids.size(); i++) {
            final int idx = i;
            final String uid = friendUids.get(i);

            dbRef.child("friends").child(currentUid).child(uid).child("displayName")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snap) {
                            String username = snap.getValue(String.class);
                            names[idx] = (username != null && !username.isEmpty())
                                    ? username : uid;

                            remaining[0]--;
                            if (remaining[0] == 0) {
                                buildAndShowDialog(context, event, friendUids, names, checked, dbRef);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            names[idx] = uid; // fall back to UID
                            remaining[0]--;
                            if (remaining[0] == 0) {
                                buildAndShowDialog(context, event, friendUids, names, checked, dbRef);
                            }
                        }
                    });
        }
    }

    private void buildAndShowDialog(Context context, EventItem event,
                                    List<String> friendUids, String[] names,
                                    boolean[] checked, DatabaseReference dbRef) {
        new AlertDialog.Builder(context)
                .setTitle("Invite friends to \"" + event.title + "\"")
                .setMultiChoiceItems(names, checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Send invites", (dialog, which) -> {
                    List<String> invited = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            invited.add(friendUids.get(i));
                        }
                    }
                    if (invited.isEmpty()) {
                        Toast.makeText(context, "No friends selected.", Toast.LENGTH_SHORT).show();
                    } else {
                        sendInvites(context, event, invited, dbRef);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendInvites(Context context, EventItem event,
                             List<String> friendUids, DatabaseReference dbRef) {
        DatabaseReference invitesRef = dbRef.child("Events").child(event.id).child("invites");

        for (String uid : friendUids) {
            invitesRef.child(uid).setValue(true);
        }

        String msg = friendUids.size() == 1
                ? "Invite sent!"
                : friendUids.size() + " invites sent!";
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvDate;
        Button btnInvite;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_event_title);
            tvDate = itemView.findViewById(R.id.tv_event_date);
            btnInvite = itemView.findViewById(R.id.btn_invite);
        }
    }
}