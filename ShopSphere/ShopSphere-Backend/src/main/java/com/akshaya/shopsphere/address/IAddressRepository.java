package com.akshaya.shopsphere.address;

import java.sql.SQLException;
import java.util.List;

public interface IAddressRepository {
    // Most recently added first, so the checkout UI can default-select the latest address.
    List<Address> getAddressesByUserId(int userId) throws SQLException;

    Address getAddressById(int addressId) throws SQLException;

    // Returns the generated address_id, or 0 on failure.
    int addAddress(Address address) throws SQLException;

    // Scoped to (addressId AND userId) so a customer can never edit another customer's address.
    boolean updateAddress(Address address) throws SQLException;

    // Scoped to (addressId AND userId) for the same reason.
    boolean deleteAddress(int addressId, int userId) throws SQLException;

    // Marks the address as default, clearing the flag on any other address of the user's first.
    boolean setDefaultAddress(int addressId, int userId) throws SQLException;
}
