package com.akshaya.shopsphere.address;

import java.sql.SQLException;
import java.util.List;

public class AddressService {
    private final IAddressRepository addressRepository;

    public AddressService(IAddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    public List<Address> getAddressesForUser(int userId) throws SQLException {
        return addressRepository.getAddressesByUserId(userId);
    }

    public Address getAddressById(int addressId) throws SQLException {
        return addressRepository.getAddressById(addressId);
    }

    public int addAddress(Address address) throws SQLException {
        validate(address);
        return addressRepository.addAddress(address);
    }

    public boolean updateAddress(Address address) throws SQLException {
        if (address.getAddressId() <= 0) {
            throw new IllegalArgumentException("Invalid address ID");
        }
        validate(address);
        return addressRepository.updateAddress(address);
    }

    public boolean deleteAddress(int addressId, int userId) throws SQLException {
        if (addressId <= 0) {
            throw new IllegalArgumentException("Invalid address ID");
        }
        return addressRepository.deleteAddress(addressId, userId);
    }

    public boolean setDefaultAddress(int addressId, int userId) throws SQLException {
        if (addressId <= 0) {
            throw new IllegalArgumentException("Invalid address ID");
        }
        return addressRepository.setDefaultAddress(addressId, userId);
    }

    private void validate(Address address) {
        if (address.getStreet() == null || address.getStreet().trim().isEmpty()) {
            throw new IllegalArgumentException("Street is required");
        }
        if (address.getCity() == null || address.getCity().trim().isEmpty()) {
            throw new IllegalArgumentException("City is required");
        }
        if (address.getPincode() == null || address.getPincode().trim().isEmpty()) {
            throw new IllegalArgumentException("Pincode is required");
        }
    }
}
