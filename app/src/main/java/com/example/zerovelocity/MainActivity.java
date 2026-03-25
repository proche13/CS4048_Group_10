package com.example.zerovelocity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private Spinner typeSpinner;
    private EditText quantityEditText;
    private Button submitButton;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        typeSpinner = findViewById(R.id.spinnerType);
        quantityEditText = findViewById(R.id.editQuantity);
        submitButton = findViewById(R.id.buttonSubmit);

        submitButton.setOnClickListener(v -> submitLog());
        }

        private void submitLog(){
        String quantityText = quantityEditText.getText().toString();

        if(quantityText.isEmpty()){
            quantityEditText.setError("Quantity is required");
            return;
        }

        LogEntry.Type type = LogEntry.Type.values()[typeSpinner.getSelectedItemPosition()];
        int quantity = Integer.parseInt(quantityText);

        LogEntry logEntry = new LogEntry(type, quantity, System.currentTimeMillis());
        }
    }
