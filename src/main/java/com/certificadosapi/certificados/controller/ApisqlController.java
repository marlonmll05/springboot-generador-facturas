package com.certificadosapi.certificados.controller;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/sql")
public class ApisqlController {

    // Método para leer el servidor desde el registro (sin cambios)
    private String getServerFromRegistry() throws Exception {
        String registryPath = "SOFTWARE\\VB and VBA Program Settings\\Asclepius\\Administrativo";
        String valueName = "Servidor";
        
        try {
            return Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, registryPath, valueName);
        } catch (Exception e) {
            throw new Exception("Error al leer el servidor desde el registro", e);
        }
    }

    // Endpoint modificado con mejor manejo de fechas
    @GetMapping("/facturas")
    public ResponseEntity<?> buscarFacturas(
        @RequestParam(required = false, name = "fechaDesde") 
        @DateTimeFormat(pattern = "yyyy-MM-dd") 
        LocalDate fechaDesde,
        
        @RequestParam(required = false, name = "fechaHasta")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate fechaHasta,
        
        @RequestParam(required = false) String idTercero,
        @RequestParam(required = false) String noContrato,
        @RequestParam(required = false) String nFact) {

        // Validación adicional de fechas
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            return ResponseEntity.badRequest().body("fechaDesde no puede ser posterior a fechaHasta");
        }

        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format("jdbc:sqlserver://%s;databaseName=IPSoftFinanciero_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;", servidor);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                StringBuilder sql = new StringBuilder();
                sql.append("SELECT FF.NFact, FF.FechaFactura, FF.TotalFactura, FF.IdMovDoc, ");
                sql.append("FF.IdTerceroKey, FF.NOMTERCERO, FF.NoContrato, FF.NOMCONTRATO ");
                sql.append("FROM IPSoft100_ST.DBO.vw_Facturas_JSON FF ");
                sql.append("WHERE 1=1");

                List<Object> params = new ArrayList<>();

                if (idTercero != null) {
                    sql.append(" AND FF.IdTerceroKey = ?");
                    params.add(idTercero);
                }
                if (noContrato != null && !noContrato.isEmpty()) {
                    sql.append(" AND FF.NoContrato = ?");
                    params.add(noContrato);
                }
                if (fechaDesde != null && fechaHasta != null) {
                    sql.append(" AND FF.FechaFactura BETWEEN ? AND ?");
                    params.add(Date.valueOf(fechaDesde));
                    params.add(Date.valueOf(fechaHasta));
                } else if (fechaDesde != null) {
                    sql.append(" AND FF.FechaFactura >= ?");
                    params.add(Date.valueOf(fechaDesde));
                } else if (fechaHasta != null) {
                    sql.append(" AND FF.FechaFactura <= ?");
                    params.add(Date.valueOf(fechaHasta));
                }
                if (nFact != null && !nFact.isEmpty()) {
                    sql.append(" AND FF.NFact = ?");
                    params.add(nFact);
                }

                sql.append(" ORDER BY FF.FECHAFACTURA DESC, FF.NomTercero, FF.NomContrato");

                try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        stmt.setObject(i + 1, params.get(i));
                    }

                    ResultSet rs = stmt.executeQuery();
                    List<Map<String, Object>> resultados = new ArrayList<>();
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    while (rs.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);

                            if ("FechaFactura".equalsIgnoreCase(columnName)) {
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                                if (value instanceof Timestamp) {
                                    fila.put(columnName, sdf.format(value));
                                } else if (value instanceof Date) {
                                    fila.put(columnName, sdf.format(new java.util.Date(((Date) value).getTime())));
                                } else {
                                    fila.put(columnName, value);
                                }
                            } else {
                                // Solo aplicar trim si el valor es una cadena
                                fila.put(columnName, (value instanceof String) ? value.toString().trim() : value);
                            }
                        }
                        resultados.add(fila);
                    }
                    return ResponseEntity.ok(resultados);
                }
            }
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body("Formato de fecha inválido. Use YYYY-MM-DD");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error en la consulta: " + e.getMessage());
        }
    }

    @GetMapping("/ejecutarRips")
    public ResponseEntity<?> ejecutarRips(@RequestParam String Nfact) {
        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                servidor
            );

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                // Construir el SQL manualmente
                String sql = "EXEC pa_Rips_JSON_Generar '" + Nfact + "', 0, 1, 1";

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql); // Ejecutar directamente

                    return ResponseEntity.ok("Procedimiento ejecutado correctamente para el cliente: " + Nfact);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al ejecutar el procedimiento: " + e.getMessage());
        }
    }

    @GetMapping("/terceros")
    public ResponseEntity<?> obtenerTerceros() {
        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format("jdbc:sqlserver://%s;databaseName=IPSoftFinanciero_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;", servidor);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "SELECT DISTINCT T.IdTerceroKey, T.NomTercero FROM IPSoftFinanciero_ST.dbo.Terceros T " +
                            "INNER JOIN IPSoft100_ST.dbo.Contratos C ON C.IdTerceroKey = T.IdTerceroKey " +
                            "ORDER BY T.NomTercero";

                try (PreparedStatement stmt = conn.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery()) {
                    List<Map<String, Object>> resultados = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        fila.put("idTerceroKey", rs.getString("IdTerceroKey"));
                        fila.put("nomTercero", rs.getString("NomTercero"));
                        resultados.add(fila);
                    }
                    return ResponseEntity.ok(resultados);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error en la consulta: " + e.getMessage());
        }
    }

    @GetMapping("/contratos")
    public ResponseEntity<?> obtenerContratos(@RequestParam String idTerceroKey) {
        if (idTerceroKey == null || idTerceroKey.isEmpty()) {
            return ResponseEntity.badRequest().body("El IdTerceroKey es requerido.");
        }
    
        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format("jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;", servidor);
    
            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "SELECT DISTINCT C.NoContrato, C.NomContrato FROM IPSoft100_ST.dbo.Contratos C WHERE C.IdTerceroKey = ? ORDER BY C.NomContrato";
    
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, idTerceroKey);
                    ResultSet rs = stmt.executeQuery();
                    List<Map<String, Object>> resultados = new ArrayList<>();
                    
                    while (rs.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        fila.put("noContrato", rs.getString("NoContrato"));
                        fila.put("nomContrato", rs.getString("NomContrato"));
                        resultados.add(fila);
                    }
                    return ResponseEntity.ok(resultados);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error en la consulta: " + e.getMessage());
        }
    }    
}


