package com.akshaya.liftmanagement;

import java.util.ArrayList;
import java.util.List;

public class LiftRepo {
    private static LiftRepo instance;
    private List<Lift> lifts;

    private LiftRepo() {
        this.lifts = new ArrayList<>();
    }

    public static LiftRepo getInstance() {
        if (instance == null) {
            instance = new LiftRepo();
        }
        return instance;
    }

    public void addLift(Lift lift) {
        lifts.add(lift);
    }

    public Lift getLiftById(int id) {
        for (Lift lift : lifts) {
            if (lift.getId() == id) {
                return lift;
            }
        }
        return null;
    }

    public List<Lift> getAllLifts() {
        return lifts;
    }
}
