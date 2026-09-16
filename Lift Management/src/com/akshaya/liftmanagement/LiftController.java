package com.akshaya.liftmanagement;

import java.util.List;
import java.util.NoSuchElementException;

public class LiftController {
    private LiftView liftView;
    private LiftService liftService;

    public LiftController(LiftView liftView, LiftService liftService) {
        this.liftView = liftView;
        this.liftService = liftService;
    }



    public void start(){
        while(true){
        try {
        int menuChoice = liftView.getMenuChoice();
        if (menuChoice == 2) {
            waitForLiftsToFinish();
            liftView.showExitMessage();
            return;
        }
        if (menuChoice != 1) {
            liftView.showErrorMessage("Enter a valid Input");
            continue;
        }
        PassengerRequest request = liftView.getHallCallRequest();
        if (request == null) {
            continue;
        }
        int pressedFloor = request.getFloor();
        LiftDirection passengerDirection = null;
        switch (request.getDirectionChoice()) {
            case 1 :
                 passengerDirection = LiftDirection.UP;
                break;
                case 2:
                passengerDirection = LiftDirection.DOWN;

                    break;
            default:
                throw new IllegalArgumentException("Wrong direction");
        }
        Lift assignedLift = liftService.assignLift(pressedFloor, passengerDirection);
        assignedLift.requestPickup(pressedFloor);
        liftView.showMessage("[Lift " + assignedLift.getId() + "] is on its way to floor " + pressedFloor + "...");
        waitForBoarding(assignedLift, pressedFloor);

        List<Integer> destinationFloors = liftView.getDestinationFloors();
        assignedLift.requestTrip(destinationFloors);

        } catch (NoSuchElementException e) {
            waitForLiftsToFinish();
            liftView.showErrorMessage("No more input. Exiting.");
            return;
        } catch (Exception e) {
            liftView.showErrorMessage(e.getMessage());
        }
    }
    }

    // Blocks until the assigned lift has physically arrived at the pickup floor
    // and opened its doors - i.e. the passenger can now board. Only then do we
    // ask them where they actually want to go.
    private void waitForBoarding(Lift lift, int floor) {
        try {
            while (!(lift.getCurrentFloor() == floor && lift.getStatus() == LiftStatus.OPEN)) {
                Thread.sleep(150);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitForLiftsToFinish() {
        System.out.println("Waiting for in-progress lifts to finish...");
        // A lift can be parked open, waiting on a destination that will now never come
        // (we're exiting) - release those first so they close up and go idle instead of
        // blocking this shutdown wait forever.
        for (Lift lift : liftService.getAllLifts()) {
            lift.cancelPendingBoarding();
        }
        for (Lift lift : liftService.getAllLifts()) {
            try {
                lift.awaitIdle();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

}
