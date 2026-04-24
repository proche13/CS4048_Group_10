package com.example.zerovelocity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.appbar.MaterialToolbar;

public class MainActivity extends AppCompatActivity {

    //sets up the main shell, default fragment, bottom navigation, and profile shortcut
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //load the feed fragment as the default screen on launch
        loadFragment(new FeedFragment());

        MaterialToolbar topBar = findViewById(R.id.top_app_bar);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        topBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_profile) {
                loadFragment(new ProfileFragment());
                return true;
            }
            return false;
        });

        //swap fragment based on which tab the user taps in bottom nav bar
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

    //replaces the visible screen inside the activities fragment container.
    private void loadFragment(Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.fragment_container, fragment);
        ft.commit();
    }
}
