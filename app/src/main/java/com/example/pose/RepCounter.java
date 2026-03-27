package com.example.pose;

public class RepCounter {
    private enum State { UP, DOWN }

    private int count = 0;
    private State currentState = State.UP;
    private static final double THRESHOLD_DOWN = 160; // Rep considered "down"
    private static final double THRESHOLD_UP = 60;   // Rep considered "up"

    public int onNewAngle(double angle) {
        if (currentState == State.UP && angle > THRESHOLD_DOWN) {
            currentState = State.DOWN;
        } else if (currentState == State.DOWN && angle < THRESHOLD_UP) {
            currentState = State.UP;
            count++;
        }
        return count;
    }

    public int getCount() {
        return count;
    }
}
