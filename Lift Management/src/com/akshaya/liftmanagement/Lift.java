package com.akshaya.liftmanagement;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

public class Lift implements Runnable {
    public static final int MIN_FLOOR = 0;
    public static final int MAX_FLOOR = 10;

    private static int counter = 1;
    private final int id;
    private volatile int currentFloor;
    private volatile LiftDirection curDirection;
    private volatile LiftStatus status;
    private final PriorityBlockingQueue<Integer> upStops;
    private final PriorityBlockingQueue<Integer> downStops;

    private final Object lock = new Object();
    private boolean threadRunning = false;
    private int currentTarget = -1; // floor already polled and being traveled to this leg, if any
    private boolean boardAtCurrentFloor = false; // pickup floor coincides with currentFloor - needs a door cycle, not travel
    private int pickupWaitFloor = -1; // if set, the lift must wait here for a destination instead of closing on a timer
    private boolean waitingForDestination = false; // true while parked open at pickupWaitFloor, waiting on a follow-up requestTrip()

    public Lift() {
        this.id = counter++;
        this.currentFloor = 0;
        this.curDirection = LiftDirection.NONE;
        this.status = LiftStatus.IDLE;
        this.upStops = new PriorityBlockingQueue<>();
        this.downStops = new PriorityBlockingQueue<>(11, Collections.reverseOrder());
    }

    public int getId() {
        return id;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public LiftDirection getCurDirection() {
        return curDirection;
    }

    public LiftStatus getStatus() {
        return status;
    }

    public void awaitIdle() throws InterruptedException {
        synchronized (lock) {
            while (threadRunning) {
                lock.wait();
            }
        }
    }

    // Each floor in the chain is compared against the one before it, not against currentFloor:
    // pickupFloor is relative to where the (empty) lift is right now (the approach leg), and each
    // destination after it is relative to the *previous* stop in the chain, since that's where the
    // passenger will actually be standing when they decide the next leg - not the lift's start point.
    public void requestTrip(List<Integer> floors) {
        enqueueTrip(floors, false);
    }

    // A hall call: send the lift to `floor` (traveling there, or opening immediately if it's
    // already there) and, once its doors are open, have it wait for the passenger's destination
    // (a follow-up requestTrip call) instead of closing again after a fixed interval - real
    // elevators don't leave before you've actually pressed a floor button.
    public void requestPickup(int floor) {
        enqueueTrip(List.of(floor), true);
    }

    // If a lift is currently parked open waiting on a destination that will never come (e.g. the
    // console's input ran out), release it so it just closes its doors and goes idle right there
    // instead of blocking forever.
    public void cancelPendingBoarding() {
        synchronized (lock) {
            if (waitingForDestination) {
                waitingForDestination = false;
                lock.notifyAll();
            }
        }
    }

    private void enqueueTrip(List<Integer> floors, boolean isPickup) {
        if (floors.isEmpty()) {
            return;
        }
        synchronized (lock) {
            int reference = currentFloor;
            boolean first = true;
            for (int floor : floors) {
                if (first && isPickup) {
                    pickupWaitFloor = floor;
                }
                if (first && floor == reference && !threadRunning) {
                    // The passenger is already standing at the lift's current floor and it isn't
                    // already busy with another trip - there's nothing to travel to, but they still
                    // need an explicit door-open cycle before the lift moves on to the next leg.
                    boardAtCurrentFloor = true;
                } else {
                    enqueueRelativeTo(floor, reference);
                }
                reference = floor;
                first = false;
            }

            if (waitingForDestination) {
                // The lift is already parked here with its doors open, waiting on exactly this
                // passenger's destination choice - wake it now that it has one.
                waitingForDestination = false;
                lock.notifyAll();
            } else if (!threadRunning) {
                threadRunning = true;
                status = LiftStatus.MOVING;
                Thread worker = new Thread(this, "Lift-" + id);
                worker.start();
            }
        }
    }

    private void enqueueRelativeTo(int floor, int referenceFloor) {
        if (floor < MIN_FLOOR || floor > MAX_FLOOR) {
            return;
        }
        if (floor == currentTarget || upStops.contains(floor) || downStops.contains(floor)) {
            return; // already pending or already the leg in progress - no duplicate stop
        }
        if (floor > referenceFloor) {
            upStops.add(floor);
        } else if (floor < referenceFloor) {
            downStops.add(floor);
        }
    }

    @Override
    public void run() {
        while (true) {
            int target;
            synchronized (lock) {
                while (true) {
                    if (boardAtCurrentFloor) {
                        boardAtCurrentFloor = false;
                        target = currentFloor;
                        currentTarget = target;
                        break;
                    }
                    if (upStops.isEmpty() && downStops.isEmpty()) {
                        status = LiftStatus.IDLE;
                        curDirection = LiftDirection.NONE;
                        threadRunning = false;
                        currentTarget = -1;
                        System.out.println("[Lift " + id + "] is idle at floor " + currentFloor);
                        lock.notifyAll();
                        return;
                    }
                    boolean goUp = upStops.isEmpty() ? false
                            : downStops.isEmpty() ? true
                            : curDirection != LiftDirection.DOWN;

                    // A stop can go stale purely from the lift continuing to move after it was queued:
                    // a floor that was correctly "ahead" when added can end up behind the lift by the
                    // time its own queue is actually serviced (some other, farther stop got polled first).
                    // Only re-bucket the queue we're about to poll from - the other queue's entries being
                    // "on the wrong side" of currentFloor right now is completely normal; they're just
                    // waiting for a later leg, not stale.
                    if (goUp) {
                        Integer head = upStops.peek();
                        if (head <= currentFloor) {
                            upStops.poll();
                            if (head < currentFloor) {
                                downStops.add(head);
                            }
                            continue;
                        }
                        curDirection = LiftDirection.UP;
                        status = LiftStatus.MOVING;
                        target = upStops.poll();
                        currentTarget = target;
                    } else {
                        Integer head = downStops.peek();
                        if (head >= currentFloor) {
                            downStops.poll();
                            if (head > currentFloor) {
                                upStops.add(head);
                            }
                            continue;
                        }
                        curDirection = LiftDirection.DOWN;
                        status = LiftStatus.MOVING;
                        target = downStops.poll();
                        currentTarget = target;
                    }
                    break;
                }
            }

            if (target == currentFloor) {
                System.out.println("[Lift " + id + "] opening doors at floor " + currentFloor + " for boarding");
            } else {
                System.out.println("[Lift " + id + "] departing floor " + currentFloor + " -> heading to floor " + target);
                while (currentFloor != target) {
                    int next = currentFloor + (curDirection == LiftDirection.UP ? 1 : -1);
                    if (next < MIN_FLOOR || next > MAX_FLOOR) {
                        System.out.println("[Lift " + id + "] refusing to move past building bounds (target " + target + ")");
                        break;
                    }
                    currentFloor = next;
                    sleep(3000);
                }
            }

            boolean needsDestination;
            synchronized (lock) {
                needsDestination = (currentFloor == pickupWaitFloor);
                if (needsDestination) {
                    pickupWaitFloor = -1;
                    waitingForDestination = true;
                }
                // status is volatile and written last, after waitingForDestination - so any thread
                // that observes OPEN via a plain getStatus() is guaranteed to also see the wait flag.
                status = LiftStatus.OPEN;
            }
            System.out.println("[Lift " + id + "] arrived at floor " + currentFloor + ", doors open");

            if (needsDestination) {
                System.out.println("[Lift " + id + "] waiting for destination selection...");
                synchronized (lock) {
                    while (waitingForDestination) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            waitingForDestination = false;
                        }
                    }
                }
            } else {
                sleep(5000);
            }
            status = LiftStatus.CLOSED;
            System.out.println("[Lift " + id + "] doors closed at floor " + currentFloor);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString() {
        synchronized (lock) {
            return "Lift [id=" + id + ", currentFloor=" + currentFloor + ", curDirection=" + curDirection
                    + ", status=" + status + ", upStops=" + upStops + ", downStops=" + downStops + "]";
        }
    }
}
