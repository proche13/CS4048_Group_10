package com.example.zerovelocity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Leaderboard screen - ranks all users by total units logged
public class LeaderboardFragment extends Fragment {

    private LeaderboardAdapter adapter;
    private TextView tvEmpty;
    private final List<LeaderboardAdapter.Entry> entries = new ArrayList<>();
    private ValueEventListener logsListener;
    private DatabaseReference logsRef;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_leaderboard, container, false);

        tvEmpty = view.findViewById(R.id.tv_empty);

        RecyclerView rv = view.findViewById(R.id.rv_leaderboard);
        adapter = new LeaderboardAdapter(entries);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        logsRef = FirebaseDatabase.getInstance("https://mostpolluted-default-rtdb.europe-west1.firebasedatabase.app/")
                .getReference("consumptionLogs");

        logsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Aggregate total units per user
                Map<String, Float> unitsByUser = new HashMap<>();
                Map<String, String> nameByUser = new HashMap<>();

                for (DataSnapshot log : snapshot.getChildren()) {
                    String uid = log.child("userID").getValue(String.class);
                    String username = log.child("username").getValue(String.class);
                    Float units = log.child("units").getValue(Float.class);

                    if (uid == null || units == null) continue;

                    unitsByUser.put(uid, unitsByUser.getOrDefault(uid, 0f) + units);
                    if (username != null) nameByUser.put(uid, username);
                }

                // Build sorted entry list (descending by total units)
                List<Map.Entry<String, Float>> sorted = new ArrayList<>(unitsByUser.entrySet());
                sorted.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

                entries.clear();
                for (int i = 0; i < sorted.size(); i++) {
                    String uid = sorted.get(i).getKey();
                    float total = sorted.get(i).getValue();
                    String name = nameByUser.getOrDefault(uid, "Unknown");
                    entries.add(new LeaderboardAdapter.Entry(i + 1, name, total));
                }

                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load leaderboard", Toast.LENGTH_SHORT).show();
            }
        };

        logsRef.addValueEventListener(logsListener);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove listener to avoid memory leaks
        if (logsRef != null && logsListener != null) {
            logsRef.removeEventListener(logsListener);
        }
    }
}
