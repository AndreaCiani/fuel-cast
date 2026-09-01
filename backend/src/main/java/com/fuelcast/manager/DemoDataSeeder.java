package com.fuelcast.manager;

import com.fuelcast.manager.model.Manager;
import com.fuelcast.manager.model.ManagerStation;
import com.fuelcast.manager.repository.ManagerRepository;
import com.fuelcast.manager.repository.ManagerStationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds a demo manager account (claiming a real Rome station with recent data)
 * so recruiters can try the Side B dashboard without registering. Idempotent:
 * runs only if the demo account does not already exist.
 */
@Component
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final ManagerRepository managers;
    private final ManagerStationRepository links;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    @Value("${app.demo.enabled:true}")
    private boolean enabled;
    @Value("${app.demo.email:demo@fuelcast.it}")
    private String demoEmail;
    @Value("${app.demo.password:demo-fuelcast}")
    private String demoPassword;

    public DemoDataSeeder(ManagerRepository managers, ManagerStationRepository links,
                          PasswordEncoder passwordEncoder, JdbcTemplate jdbc) {
        this.managers = managers;
        this.links = links;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!enabled || managers.existsByEmail(demoEmail)) return;

        Manager demo = new Manager();
        demo.setEmail(demoEmail);
        demo.setDisplayName("Gestore Demo");
        demo.setPasswordHash(passwordEncoder.encode(demoPassword));
        demo = managers.save(demo);

        Long stationId = pickRomeStation();
        if (stationId != null) {
            links.save(new ManagerStation(demo.getId(), stationId));
            log.info("Seeded demo manager {} managing station {}", demoEmail, stationId);
        } else {
            log.info("Seeded demo manager {} (no station to claim yet)", demoEmail);
        }
    }

    /** A Rome station that reported on the latest snapshot date, if any. */
    private Long pickRomeStation() {
        List<Long> ids = jdbc.query("""
                SELECT po.station_id
                FROM price_observation po
                JOIN station s ON s.id = po.station_id
                WHERE s.provincia = 'RM'
                  AND po.observed_at = (SELECT max(observed_at) FROM price_observation)
                LIMIT 1
                """, (rs, i) -> rs.getLong(1));
        return ids.isEmpty() ? null : ids.get(0);
    }
}
