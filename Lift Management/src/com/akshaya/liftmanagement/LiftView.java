package com.akshaya.liftmanagement;

import com.akshaya.liftmanagement.util.ConsoleScanner;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

public class LiftView {
private Scanner scanner = ConsoleScanner.getScanner();

public int getMenuChoice(){
    // Printed as a single call so a background lift's status line can't land in the
    // middle of the menu block even if it prints at the same moment.
    System.out.println(
        "\n============================================================\n" +
        "MENU  (lifts above may still be moving - you can request another now)\n" +
        "1.Request a Lift\n" +
        "2.Exit"
    );
    try{
        return scanner.nextInt();
    } catch(InputMismatchException e){
        scanner.next();
        return -1;
    }
}

// Hall call: only where the passenger is and which way they want to go.
// Destination floor(s) are asked separately, only after the assigned lift has
// actually arrived and opened its doors - see getDestinationFloors().
public PassengerRequest getHallCallRequest(){
    System.out.println();
    System.out.println();
    try{
    System.out.println("Enter the your current floor(MAX " + Lift.MAX_FLOOR + "):");
    int passengerFloor = scanner.nextInt();
    if (passengerFloor < Lift.MIN_FLOOR || passengerFloor > Lift.MAX_FLOOR) {
        showErrorMessage("Current floor must be between " + Lift.MIN_FLOOR + " and " + Lift.MAX_FLOOR);
        return null;
    }
    // At the ground floor there's nowhere to go but up, and at the top floor nowhere
    // to go but down - in those cases there's nothing to ask, just set it. Only when
    // both directions are genuinely possible do we actually prompt for one.
    boolean canGoUp = passengerFloor < Lift.MAX_FLOOR;
    boolean canGoDown = passengerFloor > Lift.MIN_FLOOR;
    int directionChoice;
    if (canGoUp && canGoDown) {
        System.out.println("Enter ur direction:");
        System.out.println("1.UP");
        System.out.println("2.DOWN");
        directionChoice = scanner.nextInt();
        if (directionChoice != 1 && directionChoice != 2) {
            showErrorMessage("Enter a valid Input");
            return null;
        }
    } else if (canGoUp) {
        directionChoice = 1;
        showMessage("Floor " + passengerFloor + " only allows going UP - direction set automatically.");
    } else {
        directionChoice = 2;
        showMessage("Floor " + passengerFloor + " only allows going DOWN - direction set automatically.");
    }

    return new PassengerRequest(passengerFloor, directionChoice);
    } catch(InputMismatchException e ){
        scanner.next();
        showErrorMessage("Enter a valid Input");
        return null;
    }
}

// Car call: asked once the passenger has actually boarded (doors open at their
// floor). Invalid entries are re-asked rather than abandoned, since the
// passenger is already inside the lift at this point and needs somewhere to go.
public List<Integer> getDestinationFloors(){
    List<Integer> destinationFloors = new ArrayList<>();
    boolean addingStops = true;
    while (addingStops) {
        Integer destinationFloor = readFloor("Enter destination floor(MAX " + Lift.MAX_FLOOR + "):");
        if (destinationFloor == null) {
            continue;
        }
        destinationFloors.add(destinationFloor);
        addingStops = askYesNo("Add another stop(Y/N)?");
    }
    return destinationFloors;
}

private Integer readFloor(String prompt){
    System.out.println(prompt);
    try {
        int floor = scanner.nextInt();
        if (floor < Lift.MIN_FLOOR || floor > Lift.MAX_FLOOR) {
            showErrorMessage("Floor must be between " + Lift.MIN_FLOOR + " and " + Lift.MAX_FLOOR);
            return null;
        }
        return floor;
    } catch (InputMismatchException e) {
        scanner.next();
        showErrorMessage("Enter a valid Input");
        return null;
    }
}

private boolean askYesNo(String prompt){
    while (true) {
        System.out.println(prompt);
        String choice = scanner.next().toLowerCase();
        switch (choice) {
            case "y":
                return true;
            case "n":
                return false;
            default:
                showErrorMessage("Enter valid input");
        }
    }
}

 public void showErrorMessage(String message){
    System.out.println("Error:"+message+"!");
 }

 public void showMessage(String message){
    System.out.println(message);
 }

 public void showExitMessage(){
    System.out.println("Exiting Lift Management System. Goodbye!");
 }
}
