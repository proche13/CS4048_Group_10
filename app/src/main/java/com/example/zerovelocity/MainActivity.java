package com.example.zerovelocity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
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

    private static final String THEME_PREFS = "theme_preferences";
    private static final String KEY_DARK_MODE = "dark_mode";

    private MaterialToolbar topBar;
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();

    //sets up the main shell, default fragment, bottom navigation, and profile shortcut
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //loads profile picture as button for profile
        topBar = findViewById(R.id.top_app_bar);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        setupThemeToggle();
        refreshProfileIcon();

        //load the feed fragment as the default screen only on a fresh launch.
        //Theme switches recreate this activity, and Android restores the current fragment.
        if (savedInstanceState == null) {
            loadFragment(FeedFragment.newInstance(false));
        }

        topBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_profile) {
                loadFragment(new ProfileFragment());
                return true;
            } else if (item.getItemId() == R.id.action_friends) {
                loadFragment(new FriendsFragment());
                return true;
            }
            return false;
        });

        //swap fragment based on which tab the user taps in bottom nav bar
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selected = null;

            int id = item.getItemId();
            if (id == R.id.nav_feed) {
                selected = FeedFragment.newInstance(false);
            } else if (id == R.id.nav_map) {
                selected = FeedFragment.newInstance(true);
            } else if (id == R.id.nav_log) {
                selected = new LogDrinkFragment();
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

    private void applySavedTheme() {
        AppCompatDelegate.setDefaultNightMode(isDarkModeEnabled()
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void setupThemeToggle() {
        updateThemeToggleIcon();
        topBar.setNavigationOnClickListener(v -> toggleTheme());
    }

    private boolean isDarkModeEnabled() {
        return getSharedPreferences(THEME_PREFS, MODE_PRIVATE)
                .getBoolean(KEY_DARK_MODE, false);
    }

    private void toggleTheme() {
        boolean enableDarkMode = !isDarkModeEnabled();
        getSharedPreferences(THEME_PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DARK_MODE, enableDarkMode)
                .apply();

        AppCompatDelegate.setDefaultNightMode(enableDarkMode
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void updateThemeToggleIcon() {
        if (isDarkModeEnabled()) {
            topBar.setNavigationIcon(R.drawable.ic_light_mode_24);
            topBar.setNavigationContentDescription(R.string.action_enable_light_mode);
        } else {
            topBar.setNavigationIcon(R.drawable.ic_dark_mode_24);
            topBar.setNavigationContentDescription(R.string.action_enable_dark_mode);
        }
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
    //method to refresh profile picture iin top right after profile pic change
    public void refreshProfileIcon(String profilePictureUrl) {
        if (!TextUtils.isEmpty(profilePictureUrl)) {
            setProfileIconFromUrl(profilePictureUrl);
        } else {
            refreshProfileIcon();
        }
    }

    //downloads the profile image in the background and uses it as the toolbar menu icon
    private void setProfileIconFromUrl(String profilePictureUrl) {
        imageExecutor.execute(() -> {
            try (InputStream input = new URL(profilePictureUrl).openStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap == null) {
                    return;
                }

                Bitmap iconBitmap = createCircularProfileIcon(bitmap);
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

    // crops the profile photo into a circle and draws the orange theme border around it
    private Bitmap createCircularProfileIcon(Bitmap source) {
        int size = 144;
        int borderWidth = 6;
        Bitmap scaled = Bitmap.createScaledBitmap(source, size, size, true);
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Rect rect = new Rect(0, 0, size, size);
        RectF rectF = new RectF(rect);
        float radius = size / 2f;

        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawOval(rectF, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(scaled, rect, rect, paint);
        paint.setXfermode(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(borderWidth);
        paint.setColor(Color.rgb(182, 94, 60));
        canvas.drawCircle(radius, radius, radius - borderWidth / 2f, paint);

        return output;
    }
}
