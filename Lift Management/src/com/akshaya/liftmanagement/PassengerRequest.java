package com.akshaya.liftmanagement;

// A hall call: where the passenger is standing and which way they want to go.
// The destination floor(s) are collected separately, only after the assigned
// lift has actually arrived and opened its doors for boarding - see
// LiftView.getDestinationFloors().
public class PassengerRequest {
    private final int floor;
    private final int directionChoice;

    public PassengerRequest(int floor, int directionChoice) {
        this.floor = floor;
        this.directionChoice = directionChoice;
    }

    public int getFloor() {
        return floor;
    }

    public int getDirectionChoice() {
        return directionChoice;
    }
}
