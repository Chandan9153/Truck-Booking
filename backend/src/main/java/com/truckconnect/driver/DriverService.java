package com.truckconnect.driver;

import com.truckconnect.driver.dto.DriverRequest;
import com.truckconnect.driver.dto.DriverResponse;
import com.truckconnect.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class DriverService {

    private final DriverRepository driverRepository;

    public DriverService(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Transactional
    public DriverResponse addDriver(User owner, DriverRequest req) {
        Driver driver = new Driver();
        driver.setOwner(owner);
        driver.setName(req.name().trim());
        driver.setPhone(req.phone().trim());
        driver.setLicenceNumber(req.licenceNumber().trim());
        driver.setActive(true);
        return DriverResponse.from(driverRepository.save(driver));
    }

    @Transactional
    public DriverResponse updateDriver(User owner, Long driverId, DriverRequest req) {
        Driver driver = findOwnDriver(owner, driverId);
        driver.setName(req.name().trim());
        driver.setPhone(req.phone().trim());
        driver.setLicenceNumber(req.licenceNumber().trim());
        return DriverResponse.from(driverRepository.save(driver));
    }

    /** Switch a driver off. They can never be assigned a new trip, but
        their old trips keep showing their name. There is no delete. */
    @Transactional
    public DriverResponse deactivateDriver(User owner, Long driverId) {
        Driver driver = findOwnDriver(owner, driverId);
        driver.setActive(false);
        return DriverResponse.from(driverRepository.save(driver));
    }

    /** Switch a driver back on (for example they return after a break). */
    @Transactional
    public DriverResponse activateDriver(User owner, Long driverId) {
        Driver driver = findOwnDriver(owner, driverId);
        driver.setActive(true);
        return DriverResponse.from(driverRepository.save(driver));
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> myDrivers(User owner) {
        return driverRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId())
                .stream().map(DriverResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> myActiveDrivers(User owner) {
        return driverRepository.findByOwnerIdAndActiveTrue(owner.getId())
                .stream().map(DriverResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriver(User owner, Long driverId) {
        return DriverResponse.from(findOwnDriver(owner, driverId));
    }

    /** The ownership check, in one place. If the driver does not belong
        to this owner, the repository returns empty — and the caller gets
        "not found", never "this belongs to someone else". Returning 404
        instead of 403 here is deliberate: it does not confirm that the
        driver exists at all. */
    private Driver findOwnDriver(User owner, Long driverId) {
        return driverRepository.findByIdAndOwnerId(driverId, owner.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Driver not found: " + driverId));
    }
}
