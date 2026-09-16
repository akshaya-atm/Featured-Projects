package com.akshaya.shopsphere.chat;

import com.akshaya.shopsphere.registration.RegistrationRequest;
import com.akshaya.shopsphere.registration.RegistrationService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.sql.SQLException;
import java.time.LocalDate;

public class GuestChatTools {
    private final RegistrationService registrationService;

    public GuestChatTools(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @Tool("Registers a new customer account with the given details")
    public String registerUser(
            @P("Full name") String name,
            @P("Email address") String email,
            @P("10-digit mobile number") String contact,
            @P("Birthday in YYYY-MM-DD format") String birthday
    ) {
        if (CurrentUser.getUserId() != null) {
            return "You are already logged in. Please log out first if you wish to create a new account.";
        }
        LocalDate parsedBirthday;
        try{
             parsedBirthday = LocalDate.parse(birthday);
        } catch (Exception e){
            return "Couldn't understand that birthday. Please provide it as YYYY-MM-DD.";
        }
        String tempPassword = "Temp@1234";
        RegistrationRequest registrationRequest = new RegistrationRequest(
                name,email,contact,parsedBirthday,tempPassword
        );
        try{
            boolean registered = registrationService.registerUser(registrationRequest);
            return registered ? "Successfully registered. Your temporary password is 'Temp@1234'. kindly change in the following link. after creating an account." : "Failed to register";
        } catch (IllegalArgumentException e){
            return e.getMessage();
        } catch (SQLException e){
            if (e.getSQLState() != null && (e.getSQLState().equals("23505") || e.getSQLState().equals("23000"))) {
                return "An account with this email already exists. Please login instead.";
            }
            return "Technical error: Registration could not be completed right now. Please try again later.";
        }
    }
}
