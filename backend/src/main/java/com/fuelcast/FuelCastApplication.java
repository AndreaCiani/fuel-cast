package com.fuelcast;

import com.fuelcast.ingestion.BackfillProperties;
import com.fuelcast.ingestion.IngestionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({IngestionProperties.class, BackfillProperties.class})
public class FuelCastApplication {

    public static void main(String[] args) {
        SpringApplication.run(FuelCastApplication.class, args);
    }
}
