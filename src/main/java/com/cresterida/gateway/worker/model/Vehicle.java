package com.cresterida.gateway.worker.model;
public class Vehicle {
    private String id;
    private String make;
    private String model;
    private int year;
    private String status;
    private long processingTime;
    public Vehicle(String id, String make, String model, int year) {
        this.id = id;
        this.make = make;
        this.model = model;
        this.year = year;
        this.status = "PENDING";
    }
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getProcessingTime() { return processingTime; }
    public void setProcessingTime(long processingTime) { this.processingTime = processingTime; }
    @Override
    public String toString() {
        return "Vehicle{" +
                "id='" + id + '\'' +
                ", make='" + make + '\'' +
                ", model='" + model + '\'' +
                ", year=" + year +
                ", status='" + status + '\'' +
                ", processingTime=" + processingTime +
                '}';
    }
}
