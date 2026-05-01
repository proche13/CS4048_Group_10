package com.example.zerovelocity;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.function.LongConsumer;

public class CalendarFragment extends Fragment {

    private long selectedDate;

    private EventAdapter adapter;
    private TextView tvSelectedDate;
    private TextView tvNoEvents;

    private final List<EventItem> allEvents = new ArrayList<>();

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());

    public CalendarFragment () {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        CalendarView calendarView = view.findViewById(R.id.calendarView);
        Button addEventBtn = view.findViewById(R.id.addEventBtn);
        RecyclerView recycler = view.findViewById(R.id.eventsList);
        tvSelectedDate = view.findViewById(R.id.tvSelectedDate);
        tvNoEvents = view.findViewById(R.id.tvNoEvents);

        selectedDate = getMidnight(System.currentTimeMillis());
        tvSelectedDate.setText("Events for: " + DATE_FORMAT.format(new Date(selectedDate)));

        adapter = new EventAdapter();
        // Pass the current user's UID so the adapter can load their friends for invites
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            adapter.setCurrentUid(currentUser.getUid());
        }
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        calendarView.setOnDateChangeListener((view1, year, month, dayOfMonth) -> {
            Calendar cal = Calendar.getInstance();
            cal.set(year, month, dayOfMonth,0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            selectedDate = cal.getTimeInMillis();
            tvSelectedDate.setText("Events for: " + DATE_FORMAT.format(new Date(selectedDate)));
            filterAndDisplay();
        });

        addEventBtn.setOnClickListener(v ->
                showAddEventDialog()
        );
        listenToEvents();
        return view;
    }

    private void listenToEvents(){
        DatabaseReference eventsRef = FirebaseRefs.root().child("Events");

        eventsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allEvents.clear();
                for (DataSnapshot child : snapshot.getChildren()){
                    String id = child.getKey();
                    String title = child.child("title").getValue(String.class);
                    Long date = child.child("date").getValue(Long.class);
                    Long startTime = child.child("startTime").getValue(Long.class);
                    Long endTime = child.child("endTime").getValue(Long.class);

                    if (title != null && date != null){
                        allEvents.add(new EventItem(id, title, date,
                                startTime != null ? startTime : date ,
                                endTime != null ? endTime : date));
                    }
                }
                filterAndDisplay();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (getContext() != null){
                    Toast.makeText(getContext(), "Failed to load events", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void filterAndDisplay(){
        List<EventItem> filtered = new ArrayList<>();
        for (EventItem event : allEvents){
            if (getMidnight(event.date) == selectedDate){
                filtered.add(event);
            }
        }
        adapter.setEvents(filtered);

        if (tvNoEvents != null){
            tvNoEvents.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void showAddEventDialog(){
        if (getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_event, null);

        EditText inputTitle = dialogView.findViewById(R.id.inputEventTitle);
        Button btnStartDate = dialogView.findViewById(R.id.btnPickStartDate);
        Button btnEndDate = dialogView.findViewById(R.id.btnPickEndDate);
        TextView tvStartChosen = dialogView.findViewById(R.id.tvStartChosen);
        TextView tvEndChosen = dialogView.findViewById(R.id.tvEndChosen);

        final long[] startMillis = {selectedDate};
        final long[] endMillis = {selectedDate};

        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy HH:MM", Locale.getDefault());

        btnStartDate.setOnClickListener(v -> pickDateTime(startMillis, chosen -> {
            startMillis[0] = chosen;
            tvStartChosen.setText("Start: " + fmt.format(new Date(chosen)));
        }));

        btnEndDate.setOnClickListener(v -> pickDateTime(endMillis, chosen -> {
            endMillis[0] = chosen;
            tvEndChosen.setText("End: " + fmt.format(new Date(chosen)));
        }));

        builder.setTitle("Add Event on " + DATE_FORMAT.format(new Date(selectedDate)))
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String eventTitle = inputTitle.getText().toString().trim();
                    if(eventTitle.isEmpty()){
                        Toast.makeText(getContext(), "Please enter an event name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (endMillis[0] < startMillis[0]){
                        Toast.makeText(getContext(), "End time must be after start time", Toast.LENGTH_SHORT).show();
                    }
                    saveEvent(eventTitle, selectedDate, startMillis[0], endMillis[0]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickDateTime(long[] current, LongConsumer onPicked){
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(current[0]);

        new DatePickerDialog(getContext(), (dp, year, month, day) -> {
            cal.set(year, month, day);
            new TimePickerDialog(getContext(), (tp, hour, minute) -> {
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                onPicked.accept(cal.getTimeInMillis());
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void saveEvent(String title, long date, long startTime, long endTime){
        DatabaseReference dbRef = FirebaseRefs.root().child("Events");

        String eventId = dbRef.push().getKey();

        if(eventId == null){
            return;
        }

        Map<String, Object> event = new HashMap<>();
        event.put("title", title);
        event.put("date", date);
        event.put("startTime", startTime);
        event.put("endTime", endTime);

        dbRef.child(eventId).setValue(event)
                .addOnSuccessListener(unused ->
                        Toast.makeText(getContext(), "Events saved", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),"Failed to save event", Toast.LENGTH_SHORT).show());
    }

    private long getMidnight(long timestamp){
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}