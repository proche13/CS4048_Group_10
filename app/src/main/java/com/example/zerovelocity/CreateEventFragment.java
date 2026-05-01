package com.example.zerovelocity;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreateEventFragment extends DialogFragment {

    // Static draft state — survives dismiss/re-create during location pick
    static String draftName = "";
    static String draftDesc = "";
    static long draftStartTime = 0;
    static double draftLat = 0;
    static double draftLng = 0;
    static String draftLabel = "No location set";
    static boolean hasDraft = false;

    private String myUid;
    private DatabaseReference rootRef;

    private EditText etName, etDesc;
    private MaterialButton btnPickTime, btnPickLocation, btnCreate;
    private TextView tvLocationLabel, tvNoFriends;
    private RecyclerView rvFriends;

    private long selectedStartTime = 0;
    private double selectedLat = 0, selectedLng = 0;
    private String selectedLabel = "No location set";

    private final List<FriendInviteItem> friendItems = new ArrayList<>();
    private FriendInviteAdapter friendAdapter;

    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_ZeroVelocity);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create_event, container, false);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            dismiss();
            return view;
        }
        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        rootRef = FirebaseRefs.root();

        etName = view.findViewById(R.id.et_event_name);
        etDesc = view.findViewById(R.id.et_event_desc);
        btnPickTime = view.findViewById(R.id.btn_pick_time);
        btnPickLocation = view.findViewById(R.id.btn_pick_location);
        tvLocationLabel = view.findViewById(R.id.tv_location_label);
        rvFriends = view.findViewById(R.id.rv_invite_friends);
        tvNoFriends = view.findViewById(R.id.tv_no_friends);
        btnCreate = view.findViewById(R.id.btn_create_event);

        // Restore state after returning from location pick
        if (hasDraft) {
            if (!TextUtils.isEmpty(draftName)) etName.setText(draftName);
            if (!TextUtils.isEmpty(draftDesc)) etDesc.setText(draftDesc);
            selectedStartTime = draftStartTime;
            selectedLat = draftLat;
            selectedLng = draftLng;
            selectedLabel = draftLabel;
            if (selectedStartTime > 0) btnPickTime.setText(TIME_FMT.format(new Date(selectedStartTime)));
            tvLocationLabel.setText(selectedLabel);
        }

        view.findViewById(R.id.btn_close_create).setOnClickListener(v -> {
            hasDraft = false;
            dismiss();
        });

        btnPickTime.setOnClickListener(v -> pickDateTime());

        btnPickLocation.setOnClickListener(v -> {
            if (getParentFragment() instanceof MapFragment) {
                saveDraftState();
                dismiss();
                ((MapFragment) getParentFragment()).enterLocationPickMode();
            } else {
                new LocationPickFragment().show(
                        requireActivity().getSupportFragmentManager(), "location_pick_dialog");
            }
        });

        requireActivity().getSupportFragmentManager()
                .setFragmentResultListener(LocationPickFragment.RESULT_KEY, getViewLifecycleOwner(),
                        (key, bundle) -> {
                            selectedLat = bundle.getDouble("lat");
                            selectedLng = bundle.getDouble("lng");
                            selectedLabel = bundle.getString("label", "No location set");
                            tvLocationLabel.setText(selectedLabel);
                        });

        btnCreate.setOnClickListener(v -> createEvent());

        friendAdapter = new FriendInviteAdapter(friendItems);
        rvFriends.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvFriends.setAdapter(friendAdapter);
        loadFriends();

        return view;
    }

    private void saveDraftState() {
        draftName = etName.getText() != null ? etName.getText().toString() : "";
        draftDesc = etDesc.getText() != null ? etDesc.getText().toString() : "";
        draftStartTime = selectedStartTime;
        draftLat = selectedLat;
        draftLng = selectedLng;
        draftLabel = selectedLabel;
        hasDraft = true;
    }

    private void pickDateTime() {
        Calendar cal = Calendar.getInstance();
        if (selectedStartTime > 0) cal.setTimeInMillis(selectedStartTime);
        new DatePickerDialog(requireContext(), (dp, year, month, day) -> {
            cal.set(year, month, day);
            new TimePickerDialog(requireContext(), (tp, hour, minute) -> {
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                selectedStartTime = cal.getTimeInMillis();
                btnPickTime.setText(TIME_FMT.format(new Date(selectedStartTime)));
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadFriends() {
        rootRef.child("friends").child(myUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                if (count == 0) {
                    if (isAdded()) tvNoFriends.setVisibility(View.VISIBLE);
                    return;
                }
                if (isAdded()) tvNoFriends.setVisibility(View.GONE);
                int[] remaining = {(int) count};
                for (DataSnapshot child : snapshot.getChildren()) {
                    String friendUid = child.getKey();
                    if (friendUid == null) {
                        remaining[0]--;
                        continue;
                    }
                    rootRef.child("users").child(friendUid).child("username")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snap) {
                                    String username = snap.getValue(String.class);
                                    friendItems.add(new FriendInviteItem(
                                            friendUid,
                                            !TextUtils.isEmpty(username) ? username : friendUid
                                    ));
                                    remaining[0]--;
                                    if (remaining[0] == 0 && isAdded()) {
                                        friendAdapter.notifyDataSetChanged();
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    remaining[0]--;
                                    if (remaining[0] == 0 && isAdded()) {
                                        friendAdapter.notifyDataSetChanged();
                                    }
                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) tvNoFriends.setVisibility(View.VISIBLE);
            }
        });
    }

    private void createEvent() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String desc = etDesc.getText() != null ? etDesc.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(requireContext(), "Please enter an event name", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedStartTime == 0) {
            Toast.makeText(requireContext(), "Please set a date and time", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedLat == 0 && selectedLng == 0) {
            Toast.makeText(requireContext(), "Please pick a location on the map", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference eventsRef = rootRef.child("Events");
        String eventId = eventsRef.push().getKey();
        if (eventId == null) return;

        Calendar dayCal = Calendar.getInstance();
        dayCal.setTimeInMillis(selectedStartTime);
        dayCal.set(Calendar.HOUR_OF_DAY, 0);
        dayCal.set(Calendar.MINUTE, 0);
        dayCal.set(Calendar.SECOND, 0);
        dayCal.set(Calendar.MILLISECOND, 0);

        Map<String, Object> event = new HashMap<>();
        event.put("title", name);
        event.put("description", desc);
        event.put("date", dayCal.getTimeInMillis());
        event.put("startTime", selectedStartTime);
        event.put("createdBy", myUid);
        event.put("locationLabel", selectedLabel);
        event.put("latitude", selectedLat);
        event.put("longitude", selectedLng);

        // Creator attends by default
        Map<String, Boolean> attendees = new HashMap<>();
        attendees.put(myUid, true);
        event.put("attendees", attendees);

        // Build invites
        Map<String, Object> invites = new HashMap<>();
        for (FriendInviteItem item : friendItems) {
            if (item.invited) {
                Map<String, Object> inv = new HashMap<>();
                inv.put("status", "pending");
                inv.put("canInvite", item.canInvite);
                invites.put(item.uid, inv);
            }
        }
        event.put("invites", invites);

        btnCreate.setEnabled(false);
        eventsRef.child(eventId).setValue(event)
                .addOnSuccessListener(unused -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Event created!", Toast.LENGTH_SHORT).show();
                        hasDraft = false;
                        dismiss();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Failed to create event", Toast.LENGTH_SHORT).show();
                        btnCreate.setEnabled(true);
                    }
                });
    }

    static class FriendInviteItem {
        final String uid;
        final String username;
        boolean invited;
        boolean canInvite;

        FriendInviteItem(String uid, String username) {
            this.uid = uid;
            this.username = username;
        }
    }

    static class FriendInviteAdapter extends RecyclerView.Adapter<FriendInviteAdapter.VH> {
        private final List<FriendInviteItem> items;

        FriendInviteAdapter(List<FriendInviteItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_invite_friend, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            FriendInviteItem item = items.get(position);
            holder.tvName.setText(item.username);

            // Clear listeners before setting state to avoid feedback loops
            holder.cbInvite.setOnCheckedChangeListener(null);
            holder.swCanInvite.setOnCheckedChangeListener(null);
            holder.cbInvite.setChecked(item.invited);
            holder.swCanInvite.setChecked(item.canInvite);
            holder.llCanInvite.setVisibility(item.invited ? View.VISIBLE : View.GONE);

            holder.cbInvite.setOnCheckedChangeListener((btn, checked) -> {
                item.invited = checked;
                holder.llCanInvite.setVisibility(checked ? View.VISIBLE : View.GONE);
                if (!checked) {
                    item.canInvite = false;
                    holder.swCanInvite.setChecked(false);
                }
            });
            holder.swCanInvite.setOnCheckedChangeListener((btn, checked) -> item.canInvite = checked);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final CheckBox cbInvite;
            final TextView tvName;
            final LinearLayout llCanInvite;
            final SwitchMaterial swCanInvite;

            VH(@NonNull View itemView) {
                super(itemView);
                cbInvite = itemView.findViewById(R.id.cb_invite);
                tvName = itemView.findViewById(R.id.tv_friend_name);
                llCanInvite = itemView.findViewById(R.id.ll_can_invite);
                swCanInvite = itemView.findViewById(R.id.sw_can_invite);
            }
        }
    }
}
