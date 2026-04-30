package com.example.zerovelocity;

import android.app.AlertDialog;
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

                    if (title != null && date != null){
                        allEvents.add(new EventItem(id, title, date));
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

        final EditText input = new EditText(getContext());
        input.setHint("Event Title");

        builder.setTitle("Add Event on " + DATE_FORMAT.format(new Date(selectedDate)))
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String eventTitle = input.getText().toString().trim();
                    if(eventTitle.isEmpty()){
                        Toast.makeText(getContext(), "Please enter an event name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveEvent(eventTitle, selectedDate);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveEvent(String title, long date){
        DatabaseReference dbRef = FirebaseRefs.root().child("Events");

        String eventId = dbRef.push().getKey();

        if(eventId == null){
            return;
        }

        Map<String, Object> event = new HashMap<>();
        event.put("title", title);
        event.put("date", date);

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
