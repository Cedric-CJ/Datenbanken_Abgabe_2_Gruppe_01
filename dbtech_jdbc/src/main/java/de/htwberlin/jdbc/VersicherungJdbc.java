package de.htwberlin.jdbc;

/**
 * @author Ingo Classen
 */

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import de.htwberlin.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.htwberlin.domain.Kunde;

/**
 * VersicherungJdbc
 */
public class VersicherungJdbc implements IVersicherungJdbc {
  private static final Logger L = LoggerFactory.getLogger(VersicherungJdbc.class);
  private Connection connection;

  @Override
  public void setConnection(Connection connection) {
    this.connection = connection;
  }

  @SuppressWarnings("unused")
  private Connection useConnection() {
    if (connection == null) {
      throw new DataException("Connection not set");
    }
    return connection;
  }

  @Override
  public List<String> kurzBezProdukte() {
    List<String> bezeichnungen = new LinkedList<String>();
    L.info("start");
    try (Statement s = useConnection().createStatement()) {
      ResultSet r = s.executeQuery("SELECT kurzbez FROM Produkt ORDER BY id ASC");
      while (r.next()) {
        String kurzbez = r.getString("kurzbez");
        bezeichnungen.add(kurzbez);
      }
    } catch (SQLException e) {
      throw new DataException(e);
    }
    L.info("ende");
    return bezeichnungen;
  }


  @Override
  public Kunde findKundeById(Integer id) {
    try (PreparedStatement pstmt = useConnection().prepareStatement("SELECT id, name, geburtsdatum FROM kunde WHERE id = ?")) {
      pstmt.setInt(1, id);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          int kundenId = rs.getInt("id");
          String name = rs.getString("name");
          LocalDate geburtsdatum = rs.getDate("geburtsdatum").toLocalDate();
          return new Kunde(kundenId, name, geburtsdatum);
        } else {
          throw new KundeExistiertNichtException(id);
        }
      }
    } catch (SQLException e) {
      throw new DataException(e);
    }
  }

  @Override
  public void createVertrag(Integer id, Integer produktId, Integer kundenId, LocalDate versicherungsbeginn)
          throws VertragExistiertBereitsException, ProduktExistiertNichtException, KundeExistiertNichtException,
          DatumInVergangenheitException {

    if (versicherungsbeginn.isBefore(LocalDate.now())) {
      throw new DatumInVergangenheitException(versicherungsbeginn);
    }
    try {
      if (!produktExistiert(produktId)) {
        throw new ProduktExistiertNichtException(produktId);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    try {
      if (!kundeExistiert(kundenId)) {
        throw new KundeExistiertNichtException(kundenId);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    try {
      if (vertragExistiert(id)) {
        throw new VertragExistiertBereitsException(id);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    LocalDate versicherungsende = versicherungsbeginn.plusYears(1).minusDays(1);
    String sql = "INSERT INTO Vertrag (ID, PRODUKT_FK, KUNDE_FK, VERSICHERUNGSBEGINN, VERSICHERUNGSENDE) VALUES (?, ?, ?, ?, ?)";
    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      pstmt.setInt(1, id);
      pstmt.setInt(2, produktId);
      pstmt.setInt(3, kundenId);
      pstmt.setDate(4, java.sql.Date.valueOf(versicherungsbeginn));
      pstmt.setDate(5, java.sql.Date.valueOf(versicherungsende));
      pstmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean vertragExistiert(Integer id) throws SQLException {
    String sql = "SELECT COUNT(*) FROM Vertrag WHERE ID = ?";
    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      pstmt.setInt(1, id);
      ResultSet rs = pstmt.executeQuery();
      return rs.next() && rs.getInt(1) > 0;
    }
  }

  private boolean produktExistiert(Integer produktId) throws SQLException {
    String sql = "SELECT COUNT(*) FROM Produkt WHERE ID = ?";
    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      pstmt.setInt(1, produktId);
      ResultSet rs = pstmt.executeQuery();
      return rs.next() && rs.getInt(1) > 0;
    }
  }

  private boolean kundeExistiert(Integer Id) throws SQLException {
    String sql = "SELECT COUNT(*) FROM Kunde WHERE ID = ?";
    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      pstmt.setInt(1, Id);
      ResultSet rs = pstmt.executeQuery();
      return rs.next() && rs.getInt(1) > 0;
    }
  }

  @Override
  public BigDecimal calcMonatsrate(Integer vertragsId) throws VertragExistiertNichtException {
    try {
      String sqlDeckung = "SELECT COUNT(*) FROM Deckung WHERE Vertrag_FK = ?";
      try (PreparedStatement pstmtDeckung = connection.prepareStatement(sqlDeckung)) {
        pstmtDeckung.setInt(1, vertragsId);
        try (ResultSet rsDeckung = pstmtDeckung.executeQuery()) {
          if (rsDeckung.next() && rsDeckung.getInt(1) == 0) {
            return BigDecimal.ZERO; // Keine Deckungen vorhanden.
          }
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Fehler beim Überprüfen der Deckungen: ", e);
    }
    String sql = "SELECT VERSICHERUNGSBEGINN FROM Vertrag WHERE ID = ?";
    LocalDate versicherungsbeginn = null;

    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      pstmt.setInt(1, vertragsId);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          versicherungsbeginn = rs.getDate("VERSICHERUNGSBEGINN").toLocalDate();
        } else {
          throw new VertragExistiertNichtException(vertragsId);
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

    if (versicherungsbeginn != null) {
      int jahr = versicherungsbeginn.getYear();
      switch (jahr) {
        case 2017:
          return BigDecimal.valueOf(19);
        case 2018:
          return BigDecimal.valueOf(20);
        case 2019:
          return BigDecimal.valueOf(22);
        default:
          return BigDecimal.ZERO;
      }
    }
    return BigDecimal.ZERO;
  }
}