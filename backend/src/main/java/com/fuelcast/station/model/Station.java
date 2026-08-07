package com.fuelcast.station.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A fuel station (MIMIT anagrafica). The primary key is the MIMIT station id,
 * not a generated value — the source system owns the identity.
 *
 * <p>The {@code location} geometry column is intentionally NOT mapped here: it
 * is written via native SQL (ST_MakePoint) in the ETL and read via native geo
 * queries. This keeps the JPA layer free of a spatial-type dependency. See
 * docs/03-decisions.md (D7).
 */
@Entity
@Table(name = "station")
public class Station {

    @Id
    private Long id;

    private String gestore;
    private String bandiera;

    @Column(name = "tipo_impianto")
    private String tipoImpianto;

    private String nome;
    private String indirizzo;
    private String comune;
    private String provincia;

    private Double latitude;
    private Double longitude;

    @Column(name = "first_seen_at", insertable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", insertable = false, updatable = false)
    private Instant lastSeenAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGestore() { return gestore; }
    public void setGestore(String gestore) { this.gestore = gestore; }

    public String getBandiera() { return bandiera; }
    public void setBandiera(String bandiera) { this.bandiera = bandiera; }

    public String getTipoImpianto() { return tipoImpianto; }
    public void setTipoImpianto(String tipoImpianto) { this.tipoImpianto = tipoImpianto; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getIndirizzo() { return indirizzo; }
    public void setIndirizzo(String indirizzo) { this.indirizzo = indirizzo; }

    public String getComune() { return comune; }
    public void setComune(String comune) { this.comune = comune; }

    public String getProvincia() { return provincia; }
    public void setProvincia(String provincia) { this.provincia = provincia; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
