package com.akshaya.shopsphere.chat;

public class CurrentUser {
    private static final ThreadLocal<Integer> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_TYPE = new ThreadLocal<>();

    public static void set(Integer userId){
        USER_ID.set(userId);
    }

    public static void set(Integer userId, String userType){
        USER_ID.set(userId);
        USER_TYPE.set(userType);
    }

    public static Integer getUserId(){
        return USER_ID.get();
    }

    public static String getUserType(){
        return USER_TYPE.get();
    }

    public static boolean isAdmin(){
        return "ADMIN".equalsIgnoreCase(USER_TYPE.get());
    }

    public static void clear(){
        USER_ID.remove();
        USER_TYPE.remove();
    }
}
