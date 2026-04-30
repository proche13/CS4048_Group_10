package com.example.zerovelocity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Leaderboard screen - global or friends-only ranking by total units logged
public class LeaderboardFragment extends Fragment {

    private LeaderboardAdapter adapter;
    private final List<LeaderboardAdapter.Entry> adapterEntries = new ArrayList<>();

    private MaterialCardView podiumCard, rankingsCard, emptyCard;
    private TextView name1, units1, initial1;
    private TextView name2, units2, initial2;
    private TextView name3, units3, initial3;
    private TextView tvSubtitle;
    private LinearLayout podium2, podium3;

    private DatabaseReference logsRef, friendsRef;
    private ValueEventListener logsListener, friendsListener;

    // Maps keyed by uid — one per category
    private final Map<String, Float> drinkUnitsByUser     = new HashMap<>();
    private final Map<String, Float> cigarettesByUser     = new HashMap<>();
    private final Map<String, String> nameByUser          = new HashMap<>();
    private final Set<String> friendUids                  = new HashSet<>();

    private String myUid;
    private boolean friendsModeActive   = false;
    private LogEntry.Category activeCategory = LogEntry.Category.Drink;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_leaderboard, container, false);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) myUid = currentUser.getUid();

        tvSubtitle = view.findViewById(R.id.tv_subtitle);

        // Podium views
        podiumCard = view.findViewById(R.id.podium_card);
        name1 = view.findViewById(R.id.name_1);
        units1 = view.findViewById(R.id.units_1);
        initial1 = view.findViewById(R.id.initial_1);
        name2 = view.findViewById(R.id.name_2);
        units2 = view.findViewById(R.id.units_2);
        initial2 = view.findViewById(R.id.initial_2);
        name3 = view.findViewById(R.id.name_3);
        units3 = view.findViewById(R.id.units_3);
        initial3 = view.findViewById(R.id.initial_3);
        podium2 = view.findViewById(R.id.podium_2);
        podium3 = view.findViewById(R.id.podium_3);

        rankingsCard = view.findViewById(R.id.rankings_card);
        emptyCard = view.findViewById(R.id.empty_card);

        RecyclerView rv = view.findViewById(R.id.rv_leaderboard);
        adapter = new LeaderboardAdapter(adapterEntries);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        // Toggle: Global / Friends
        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggle_mode);
        toggle.check(R.id.btn_global);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                friendsModeActive = (checkedId == R.id.btn_friends);
                rebuildLeaderboard();
            }
        });

        // Toggle: category (Drinks / Cigarettes)
        MaterialButtonToggleGroup toggleCat = view.findViewById(R.id.toggle_category);
        toggleCat.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_lb_drink)          activeCategory = LogEntry.Category.Drink;
            else if (checkedId == R.id.btn_lb_cigarette) activeCategory = LogEntry.Category.Cigarette;
            updateSubtitle();
            rebuildLeaderboard();
        });

        DatabaseReference dbRef = FirebaseRefs.root();

        // Listen to consumption logs
        logsRef = dbRef.child("consumptionLogs");
        logsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                drinkUnitsByUser.clear();
                cigarettesByUser.clear();
                nameByUser.clear();
                for (DataSnapshot log : snapshot.getChildren()) {
                    String uid      = log.child("userID").getValue(String.class);
                    String username = log.child("username").getValue(String.class);
                    String category = log.child("category").getValue(String.class);
                    Float units     = log.child("units").getValue(Float.class);
                    if (uid == null || units == null || category == null) continue;
                    if (username != null) nameByUser.put(uid, username);
                    switch (category) {
                        case "Drink":
                            drinkUnitsByUser.put(uid, drinkUnitsByUser.getOrDefault(uid, 0f) + units);
                            break;
                        case "Cigarette":
                            cigarettesByUser.put(uid, cigarettesByUser.getOrDefault(uid, 0f) + units);
                            break;
                    }
                }
                rebuildLeaderboard();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (getContext() != null)
                    Toast.makeText(getContext(), "Failed to load leaderboard", Toast.LENGTH_SHORT).show();
            }
        };
        logsRef.addValueEventListener(logsListener);

        // Listen to current user's friends list for the friends filter
        if (myUid != null) {
            friendsRef = dbRef.child("friends").child(myUid);
            friendsListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    friendUids.clear();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String uid = child.child("uid").getValue(String.class);
                        if (uid != null) friendUids.add(uid);
                    }
                    if (friendsModeActive) rebuildLeaderboard();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            };
            friendsRef.addValueEventListener(friendsListener);
        }

        return view;
    }

    private void updateSubtitle() {
        if (tvSubtitle == null) return;
        switch (activeCategory) {
            case Drink:      tvSubtitle.setText("Ranked by standard drinks");   break;
            case Cigarette:  tvSubtitle.setText("Ranked by cigarettes smoked"); break;
        }
    }

    // Rebuilds the sorted list based on the current mode and active category
    private void rebuildLeaderboard() {
        Map<String, Float> source;
        switch (activeCategory) {
            case Cigarette: source = cigarettesByUser; break;
            default:        source = drinkUnitsByUser; break;
        }

        List<Map.Entry<String, Float>> sorted = new ArrayList<>();
        for (Map.Entry<String, Float> e : source.entrySet()) {
            if (!friendsModeActive
                    || e.getKey().equals(myUid)
                    || friendUids.contains(e.getKey())) {
                sorted.add(e);
            }
        }
        sorted.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

        List<LeaderboardAdapter.Entry> all = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            String uid = sorted.get(i).getKey();
            String name = nameByUser.getOrDefault(uid, "Unknown");
            all.add(new LeaderboardAdapter.Entry(i + 1, name, sorted.get(i).getValue()));
        }

        bindPodium(all);

        adapterEntries.clear();
        for (int i = 3; i < all.size(); i++) adapterEntries.add(all.get(i));
        adapter.notifyDataSetChanged();

        boolean hasData = !all.isEmpty();
        podiumCard.setVisibility(hasData ? View.VISIBLE : View.GONE);
        rankingsCard.setVisibility(adapterEntries.isEmpty() ? View.GONE : View.VISIBLE);
        emptyCard.setVisibility(hasData ? View.GONE : View.VISIBLE);
    }

    // Binds the top 3 entries directly to the podium views; hides unused slots
    private void bindPodium(List<LeaderboardAdapter.Entry> entries) {
        if (!entries.isEmpty()) {
            bindSlot(entries.get(0), name1, units1, initial1);
        }
        if (entries.size() >= 2) {
            bindSlot(entries.get(1), name2, units2, initial2);
            podium2.setVisibility(View.VISIBLE);
        } else {
            podium2.setVisibility(View.INVISIBLE);
        }
        if (entries.size() >= 3) {
            bindSlot(entries.get(2), name3, units3, initial3);
            podium3.setVisibility(View.VISIBLE);
        } else {
            podium3.setVisibility(View.INVISIBLE);
        }
    }

    private void bindSlot(LeaderboardAdapter.Entry e, TextView nameView,
                          TextView unitsView, TextView initialView) {
        nameView.setText(e.displayName);
        unitsView.setText(String.format(Locale.getDefault(), "%.1f u", e.totalUnits));
        if (!e.displayName.isEmpty()) {
            initialView.setText(
                    String.valueOf(e.displayName.charAt(0)).toUpperCase(Locale.getDefault()));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (logsRef != null && logsListener != null) logsRef.removeEventListener(logsListener);
        if (friendsRef != null && friendsListener != null) friendsRef.removeEventListener(friendsListener);
    }
}
