package com.example.zerovelocity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

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

        ArrayAdapter<LogEntry.Category> adapter = new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_item, LogEntry.Category.values());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);

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

        float units;

        try{
            units = Float.parseFloat(unitsText);
        }catch (NumberFormatException e){
            unitsEditText.setError("Invalid units. Please enter a valid number");
            return;
        }

        LogEntry.Category category = (LogEntry.Category) categorySpinner.getSelectedItem();

        viewModel.logEvent(category, "default", "default", units);

        Toast.makeText(requireContext(), "Logged", Toast.LENGTH_SHORT).show();
        unitsEditText.setText("");
    }
}
