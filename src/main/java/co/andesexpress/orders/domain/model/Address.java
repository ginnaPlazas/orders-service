package co.andesexpress.orders.domain.model;

public final class Address {

    private final int departmentId;
    private final int cityId;

    public Address(int departmentId, int cityId) {
        if (departmentId <= 0 || cityId <= 0) {
            throw new IllegalArgumentException("Departamento y ciudad deben ser ids válidos");
        }
        this.departmentId = departmentId;
        this.cityId = cityId;
    }

    public int getDepartmentId() { return departmentId; }
    public int getCityId() { return cityId; }
}