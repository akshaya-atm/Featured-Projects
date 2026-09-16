package com.akshaya.shopsphere.address;

public class Address {
    private int addressId;
    private int userId;
    private String houseNo;
    private String street;
    private String landmark;
    private String city;
    private String state;
    private String pincode;
    // Named "defaultAddress" not "default" -- "default" is a reserved word in Java.
    private boolean defaultAddress;

    public Address() {
    }

    public Address(int addressId, int userId, String houseNo, String street, String landmark, String city, String state, String pincode) {
        this.addressId = addressId;
        this.userId = userId;
        this.houseNo = houseNo;
        this.street = street;
        this.landmark = landmark;
        this.city = city;
        this.state = state;
        this.pincode = pincode;
    }

    public Address(int addressId, int userId, String houseNo, String street, String landmark, String city, String state, String pincode, boolean defaultAddress) {
        this.addressId = addressId;
        this.userId = userId;
        this.houseNo = houseNo;
        this.street = street;
        this.landmark = landmark;
        this.city = city;
        this.state = state;
        this.pincode = pincode;
        this.defaultAddress = defaultAddress;
    }

    public int getAddressId() {
        return addressId;
    }

    public void setAddressId(int addressId) {
        this.addressId = addressId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getHouseNo() {
        return houseNo;
    }

    public void setHouseNo(String houseNo) {
        this.houseNo = houseNo;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getLandmark() {
        return landmark;
    }

    public void setLandmark(String landmark) {
        this.landmark = landmark;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public boolean isDefaultAddress() {
        return defaultAddress;
    }

    public void setDefaultAddress(boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
    }
}
