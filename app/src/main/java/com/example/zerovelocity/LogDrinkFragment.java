package com.example.zerovelocity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

// Log Drink screen - allows user to log a drink event
public class LogDrinkFragment extends Fragment {

    private Spinner categorySpinner;
    private EditText unitsEditText;
    private Button logButton;

    private LogDrinkViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log_drink, container, false);

        categorySpinner = view.findViewById(R.id.spinnerCategory);
        unitsEditText = view.findViewById(R.id.editTextUnits);
        logButton = view.findViewById(R.id.buttonLog);

        viewModel = new ViewModelProvider(this).get(LogDrinkViewModel.class);

        logButton.setOnClickListener(v -> submit());

        return view;
    }

    private void submit(){
        String unitsText = unitsEditText.getText().toString();

        if(unitsText.isEmpty()){
            unitsEditText.setError("Units required");
            return;
        }

        float units = Float.parseFloat(unitsText);
        LogEntry.Category category = (LogEntry.Category) categorySpinner.getSelectedItem();

        viewModel.logEvent(category, "default", "default", units);
    }
}
