package com.example.zerovelocity;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private final List<EventItem> events = new ArrayList<>();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());

    public void setEvents(List<EventItem> newEvents){
        events.clear();
        events.addAll(newEvents);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position){
        EventItem event = events.get(position);
        holder.tvTitle.setText(event.title);
        holder.tvDate.setText(DATE_FORMAT.format(new Date(event.date)));
    }

    @Override
    public int getItemCount(){
        return events.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder{
        TextView tvTitle;
        TextView tvDate;

        EventViewHolder (@NonNull View itemView){
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_event_title);
            tvDate = itemView.findViewById(R.id.tv_event_date);
        }
    }
}
