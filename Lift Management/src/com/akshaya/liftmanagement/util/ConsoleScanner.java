package com.akshaya.liftmanagement.util;

import java.util.Scanner;

public class ConsoleScanner {
    private static ConsoleScanner consoleScanner;
    private Scanner scanner;
    private ConsoleScanner(){
        this.scanner = new Scanner(System.in);
    }
    public  static Scanner getScanner(){
        if(consoleScanner==null){
            consoleScanner = new ConsoleScanner();
        }
        return consoleScanner.scanner;
    }
}
