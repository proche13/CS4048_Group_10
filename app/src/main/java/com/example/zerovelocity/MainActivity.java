package com.example.zerovelocity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private MaterialToolbar topBar;
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();

    //sets up the main shell, default fragment, bottom navigation, and profile shortcut
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //load the feed fragment as the default screen on launch
        loadFragment(new FeedFragment());
        //loads profile picture as button for profile
        topBar = findViewById(R.id.top_app_bar);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        refreshProfileIcon();

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
            } else if (id == R.id.nav_calendar) {
                selected = new CalendarFragment();
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

    //loads the current users profile image into the toolbar profile action
    public void refreshProfileIcon() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || topBar == null) {
            return;
        }

        Uri authPhoto = user.getPhotoUrl();
        if (authPhoto != null) {
            setProfileIconFromUrl(authPhoto.toString());
            return;
        }

        FirebaseDatabase
                .getInstance("https://mostpolluted-default-rtdb.europe-west1.firebasedatabase.app/")
                .getReference("users")
                .child(user.getUid())
                .child("profilePictureUrl")
                .get()
                .addOnSuccessListener(snapshot -> {
                    String profilePictureUrl = snapshot.getValue(String.class);
                    if (!TextUtils.isEmpty(profilePictureUrl)) {
                        setProfileIconFromUrl(profilePictureUrl);
                    }
                });
    }

    //downloads the profile image in the background and uses it as the toolbar menu icon
    private void setProfileIconFromUrl(String profilePictureUrl) {
        imageExecutor.execute(() -> {
            try (InputStream input = new URL(profilePictureUrl).openStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap == null) {
                    return;
                }

                Bitmap iconBitmap = Bitmap.createScaledBitmap(bitmap, 96, 96, true);
                runOnUiThread(() -> {
                    MenuItem profileItem = topBar.getMenu().findItem(R.id.action_profile);
                    if (profileItem != null) {
                        profileItem.setIcon(new BitmapDrawable(getResources(), iconBitmap));
                    }
                });
            } catch (Exception ignored) {
            }
        });
    }
}
