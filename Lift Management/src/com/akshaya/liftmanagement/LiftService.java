package com.akshaya.liftmanagement;

import java.util.List;

public class LiftService {
    private LiftRepo repo;

    public LiftService(LiftRepo repo) {
        this.repo = repo;
    }

    public Lift assignLift(int floor, LiftDirection passengerDirection) {
        List<Lift> lifts = repo.getAllLifts();
        Lift nearest = null;
        int minDistance = Integer.MAX_VALUE;
        for (Lift lift : lifts) {
            LiftStatus status = lift.getStatus();
            int liftFloor = lift.getCurrentFloor();
            if (status == LiftStatus.IDLE) {
                int curDis = Math.abs(floor - liftFloor);
                if (minDistance > curDis) {
                    minDistance = curDis;
                    nearest = lift;
                }
            } else if (lift.getCurDirection() == passengerDirection) {
                boolean canPickUp = (passengerDirection == LiftDirection.DOWN && liftFloor >= floor)
                        || (passengerDirection == LiftDirection.UP && liftFloor <= floor);
                if (canPickUp) {
                    int curDis = Math.abs(floor - liftFloor);
                    if (minDistance > curDis) {
                        minDistance = curDis;
                        nearest = lift;
                    }
                }
            }
        }

        if (nearest == null) {
            throw new IllegalStateException("No lift available.Please Wait...");
        }
        return nearest;

    }

    public List<Lift> getAllLifts() {
        return repo.getAllLifts();
    }
}
