package com.example.zerovelocity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class LogRepo {
    private static LogRepo instance;
    private final List<LogEntry> logs = new List<LogEntry>() {
        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean contains(@Nullable Object o) {
            return false;
        }

        @NonNull
        @Override
        public Iterator<LogEntry> iterator() {
            return null;
        }

        @NonNull
        @Override
        public Object[] toArray() {
            return new Object[0];
        }

        @NonNull
        @Override
        public <T> T[] toArray(@NonNull T[] a) {
            return null;
        }

        @Override
        public boolean add(LogEntry logEntry) {
            return false;
        }

        @Override
        public boolean remove(@Nullable Object o) {
            return false;
        }

        @Override
        public boolean containsAll(@NonNull Collection<?> c) {
            return false;
        }

        @Override
        public boolean addAll(@NonNull Collection<? extends LogEntry> c) {
            return false;
        }

        @Override
        public boolean addAll(int index, @NonNull Collection<? extends LogEntry> c) {
            return false;
        }

        @Override
        public boolean removeAll(@NonNull Collection<?> c) {
            return false;
        }

        @Override
        public boolean retainAll(@NonNull Collection<?> c) {
            return false;
        }

        @Override
        public void clear() {

        }

        @Override
        public LogEntry get(int index) {
            return null;
        }

        @Override
        public LogEntry set(int index, LogEntry element) {
            return null;
        }

        @Override
        public void add(int index, LogEntry element) {

        }

        @Override
        public LogEntry remove(int index) {
            return null;
        }

        @Override
        public int indexOf(@Nullable Object o) {
            return 0;
        }

        @Override
        public int lastIndexOf(@Nullable Object o) {
            return 0;
        }

        @NonNull
        @Override
        public ListIterator<LogEntry> listIterator() {
            return null;
        }

        @NonNull
        @Override
        public ListIterator<LogEntry> listIterator(int index) {
            return null;
        }

        @NonNull
        @Override
        public List<LogEntry> subList(int fromIndex, int toIndex) {
            return Collections.emptyList();
        }
    };

    private LogRepo() {
    }

    private static LogRepo getInstance(){
        if(instance == null){
            instance = new LogRepo();
        }
        return instance;
    }

    public void addLog(LogEntry entry){
        logs.add(entry);
    }

    public List<LogEntry> getLogs(){
        return logs;
    }
}
