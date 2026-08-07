package com.fuelcast.station.dto;

import java.util.List;

/** Full station view: identity + per-fuel historical stats over the window. */
public record StationDetail(
        long id,
        String nome,
        String bandiera,
        String tipoImpianto,
        String indirizzo,
        String comune,
        String provincia,
        Double latitude,
        Double longitude,
        int windowDays,
        List<FuelStat> fuels) {
}
