package com.example.zerovelocity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Load the Feed fragment as the default screen on launch
        loadFragment(new FeedFragment());

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        // Swap fragment based on which tab the user taps
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selected = null;

            int id = item.getItemId();
            if (id == R.id.nav_feed) {
                selected = new FeedFragment();
            } else if (id == R.id.nav_log) {
                selected = new LogDrinkFragment();
            } else if (id == R.id.nav_friends) {
                selected = new FriendsFragment();
            } else if (id == R.id.nav_leaderboard) {
                selected = new LeaderboardFragment();
            }

            if (selected != null) {
                loadFragment(selected);
                return true;
            }
            return false;
        });
    }

    // Helper method to load a fragment into the container
    private void loadFragment(Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.fragment_container, fragment);
        ft.commit();
    }
}
