package com.starkgroup.vms.exception;

public class DuplicateVehicleNumberException extends RuntimeException {

    private final String vehicleNumber;

    public DuplicateVehicleNumberException(String vehicleNumber) {
        super("A vehicle with number '" + vehicleNumber + "' is already registered");
        this.vehicleNumber = vehicleNumber;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }
}
