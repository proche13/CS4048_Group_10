package com.example.zerovelocity;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class CalendarFragment extends Fragment {

    private long selectedDate;

    public CalendarFragment () {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        CalendarView calendarView = view.findViewById(R.id.calendarView);
        Button addEventBtn = view.findViewById(R.id.addEventBtn);

        calendarView.setOnDateChangeListener((view1, year, month, dayOfMonth) -> {
            Calendar cal = Calendar.getInstance();
            cal.set(year, month, dayOfMonth);
            selectedDate = cal.getTimeInMillis();
        });
        addEventBtn.setOnClickListener(v -> {
            showAddEventDialog();
        });
        return view;
    }

    private void showAddEventDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

        final EditText input = new EditText(getContext());
        input.setText("Event Title");

        builder.setTitle("Add Event")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String eventTitle = input.getText().toString();
                    saveEvent(eventTitle, selectedDate);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveEvent(String title, long date){
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("Events");

        String eventId = dbRef.push().getKey();

        Map<String, Object> event = new HashMap<>();
        event.put("title", title);
        event.put("date", date);

        dbRef.child(eventId).setValue(event);
    }
}
