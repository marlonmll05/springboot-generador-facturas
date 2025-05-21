package com.certificadosapi.certificados.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.platform.win32.Advapi32Util;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;



@RestController
@RequestMapping("/certificados")
public class CertificadoController {

    private String getServerFromRegistry() throws Exception {
        String registryPath = "SOFTWARE\\VB and VBA Program Settings\\Asclepius\\Administrativo";
        String valueName = "Servidor"; // Replace with the actual value name

        try {
            // Leer el valor del registro
            return Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, registryPath, valueName);
        } catch (Exception e) {
            throw new Exception("Error al leer el registro: " + e.getMessage());
        }
    }

    @GetMapping("/{idMovDoc}")
    public ResponseEntity<byte[]> exportDocXml(
            @PathVariable int idMovDoc) {
        Connection conn = null;

        try {
            String servidor = getServerFromRegistry(); // Asumo que este método existe y funciona
            String connectionUrl = String.format("jdbc:sqlserver://%s;databaseName=IPSoftFinanciero_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;", servidor);

            conn = DriverManager.getConnection(connectionUrl);

            String query = "SELECT CONVERT(XML, DocXmlEnvelope) AS DocXml FROM MovimientoDocumentos WHERE IdMovDoc = ?";
            PreparedStatement pstmt = conn.prepareStatement(query);
            pstmt.setInt(1, idMovDoc);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String docXmlContent = rs.getString("DocXml");
                if (docXmlContent != null) {
                    // Si el contenido XML no es nulo, lo devolvemos con un OK (200)
                    return ResponseEntity.ok(docXmlContent.getBytes(StandardCharsets.UTF_8));
                } else {
                    // Si DocXml es nulo en la base de datos para el ID
                    System.out.println("El campo DocXmlEnvelope para IdMovDoc " + idMovDoc + " es nulo.");
                    // Retornamos un 404 NOT_FOUND con un mensaje específico
                    String errorMessage = "El campo DocXmlEnvelope está vacío para el ID proporcionado.";
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorMessage.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                // Si no se encontraron resultados para el IdMovDoc
                System.out.println("No se encontraron resultados para el IdMovDoc: " + idMovDoc);
                // Retornamos un 404 NOT_FOUND con un mensaje específico
                String errorMessage = "No se encontró ningún documento para el ID proporcionado.";
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorMessage.getBytes(StandardCharsets.UTF_8));
            }

        } catch (Exception e) {
            System.err.println("Error obteniendo el DocXml: " + e.getMessage());
            // En caso de cualquier otra excepción, devolvemos un 500 INTERNAL_SERVER_ERROR
            String errorMessage = "Error obteniendo el DocXml: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage.getBytes(StandardCharsets.UTF_8));
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("Error cerrando la conexión: " + e.getMessage());
                    // No se puede retornar aquí, solo loguear el error
                }
            }
        }
    }
    
    @GetMapping("/generarjson/{idMovDoc}")
    public ResponseEntity<byte[]> generarjson(@PathVariable int idMovDoc) {

        Connection conn = null;
    
        try {
             // Obtener el nombre del servidor desde el registro
            String servidor = getServerFromRegistry();

            String connectionUrl = String.format("jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;", servidor);
            
            conn = DriverManager.getConnection(connectionUrl);
    
            String facturasQuery = "SELECT IdEmpresaGrupo, NFact, tipoNota, numNota FROM dbo.Rips_Transaccion WHERE IdMovDoc = ?";
            PreparedStatement pstmt = conn.prepareStatement(facturasQuery);
            pstmt.setInt(1, idMovDoc);
            ResultSet facturasRs = pstmt.executeQuery();
    
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode resultado = mapper.createObjectNode();
    
            ArrayNode usuariosNode = mapper.createArrayNode();
    
            while (facturasRs.next()) {
                // Manejo de valores de facturas
                String numDocumentoIdObligado = facturasRs.getString("IdEmpresaGrupo");
                String numFactura = facturasRs.getString("NFact");
                String tipoNota = facturasRs.getString("tipoNota");
                String numNota = facturasRs.getString("numNota");
    
                resultado.put("numDocumentoIdObligado", numDocumentoIdObligado);
                resultado.put("numFactura", numFactura);
                resultado.put("tipoNota", tipoNota);
                resultado.put("numNota", numNota);
    
                String usuariosQuery = "SELECT IdRips_Usuario, tipoDocumentoIdentificacion, numDocumentoIdentificacion, tipoUsuario, fechaNacimiento, codSexo, CodPaisResidencia, ResHabitual, codZonaTerritorialResidencia, incapacidad, consecutivo, codPaisOrigen FROM dbo.Rips_Usuarios INNER JOIN dbo.Rips_Transaccion ON dbo.Rips_Transaccion.IdRips=dbo.Rips_Usuarios.IdRips \n" + //
                                        "WHERE dbo.Rips_Transaccion.IdMovDoc = ?";
                PreparedStatement usuariosStmt = conn.prepareStatement(usuariosQuery);
                usuariosStmt.setInt(1, idMovDoc);
                ResultSet usuariosRs = usuariosStmt.executeQuery();
                
    
                while (usuariosRs.next()) {
                    
                    ObjectNode usuarioNode = mapper.createObjectNode();

                    int idRipsUsuario = usuariosRs.getInt("IdRips_Usuario");

                    // Manejo de valores sin reemplazo por ""
                    usuarioNode.put("tipoDocumentoIdentificacion", usuariosRs.getString("tipoDocumentoIdentificacion"));
                    usuarioNode.put("numDocumentoIdentificacion", usuariosRs.getString("numDocumentoIdentificacion"));
                    usuarioNode.put("tipoUsuario", usuariosRs.getString("tipoUsuario"));
    
                    Timestamp fechaNacimientoTimestamp = usuariosRs.getTimestamp("fechaNacimiento");
                    if (fechaNacimientoTimestamp != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        String fechaNacimientoStr = sdf.format(fechaNacimientoTimestamp);
                        usuarioNode.put("fechaNacimiento", fechaNacimientoStr);
                    }
    
                    usuarioNode.put("codSexo", usuariosRs.getString("codSexo"));
                    usuarioNode.put("codPaisResidencia", usuariosRs.getString("codPaisResidencia"));
                    usuarioNode.put("codMunicipioResidencia", usuariosRs.getString("ResHabitual")); 
                    usuarioNode.put("codZonaTerritorialResidencia", usuariosRs.getString("codZonaTerritorialResidencia"));
                    usuarioNode.put("incapacidad", usuariosRs.getString("incapacidad"));
                    usuarioNode.put("consecutivo", usuariosRs.getInt("consecutivo"));
                    usuarioNode.put("codPaisOrigen", usuariosRs.getString("codPaisOrigen"));
    
                    ObjectNode serviciosNode = mapper.createObjectNode();
    
                    // Nuevo array de consultas
                    String consultasQuery = "SELECT C.codPrestador, C.fechaInicioAtencion, C.numAutorizacion, C.codConsulta, C.modalidadGrupoServicioTecSal, C.grupoServicios, C.codServicio\n" + //
                                                ", C.finalidadTecnologiaSalud, C.causaMotivoAtencion, C.codDiagnosticoPrincipal, C.codDiagnosticoRelacionado1, C.codDiagnosticoRelacionado2\n" + //
                                                ", C.codDiagnosticoRelacionado3, C.tipoDiagnosticoPrincipal, C.tipoDocumentoIdentificacion, C.numDocumentoIdentificacion, C.vrServicio\n" + //
                                                ", C.tipoPagoModerador, C.valorPagoModerador, C.numFEVPagoModerador, C.consecutivo \n" + //
                                                "FROM dbo.Rips_Consulta C\n" + //
                                                "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=C.IdRips_Usuario\n" + //
                                                "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" + //
                                                "WHERE C.IdRips_Usuario = ?";

                    PreparedStatement consultasStmt = conn.prepareStatement(consultasQuery);
                    consultasStmt.setInt(1, idRipsUsuario);
                    ResultSet consultasRs = consultasStmt.executeQuery();
    
                    ArrayNode consultasNode = mapper.createArrayNode();
                    while (consultasRs.next()) {
                        ObjectNode consultaNode = mapper.createObjectNode();
                        consultaNode.put("codPrestador", consultasRs.getString("codPrestador"));
    
                        // Manejo del campo DATETIME
                        Timestamp fechaInicioAtencionTimestamp = consultasRs.getTimestamp("fechaInicioAtencion");
                        if (fechaInicioAtencionTimestamp != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm"); 
                            String fechaInicioAtencionStr = sdf.format(fechaInicioAtencionTimestamp);
                            consultaNode.put("fechaInicioAtencion", fechaInicioAtencionStr);
                        }
    
                        consultaNode.put("numAutorizacion", consultasRs.getString("numAutorizacion"));
                        consultaNode.put("codConsulta", consultasRs.getString("codConsulta"));
                        consultaNode.put("modalidadGrupoServicioTecSal", consultasRs.getString("modalidadGrupoServicioTecSal"));
                        consultaNode.put("grupoServicios", consultasRs.getString("grupoServicios"));
                        consultaNode.put("codServicio", consultasRs.getInt("codServicio"));
                        consultaNode.put("finalidadTecnologiaSalud", consultasRs.getString("finalidadTecnologiaSalud"));
                        consultaNode.put("causaMotivoAtencion", consultasRs.getString("causaMotivoAtencion"));
                        consultaNode.put("codDiagnosticoPrincipal", consultasRs.getString("codDiagnosticoPrincipal"));
                        consultaNode.put("codDiagnosticoRelacionado1", consultasRs.getString("codDiagnosticoRelacionado1"));
                        consultaNode.put("codDiagnosticoRelacionado2", consultasRs.getString("codDiagnosticoRelacionado2"));
                        consultaNode.put("codDiagnosticoRelacionado3", consultasRs.getString("codDiagnosticoRelacionado3"));
                        consultaNode.put("tipoDiagnosticoPrincipal", consultasRs.getString("tipoDiagnosticoPrincipal"));
                        consultaNode.put("tipoDocumentoIdentificacion", consultasRs.getString("tipoDocumentoIdentificacion"));
                        consultaNode.put("numDocumentoIdentificacion", consultasRs.getString("numDocumentoIdentificacion"));
                        consultaNode.put("vrServicio", consultasRs.getInt("vrServicio"));
                        consultaNode.put("conceptoRecaudo", consultasRs.getString("tipoPagoModerador")); 
                        consultaNode.put("valorPagoModerador", consultasRs.getInt("valorPagoModerador"));
                        consultaNode.put("numFEVPagoModerador", consultasRs.getString("numFEVPagoModerador"));
                        consultaNode.put("consecutivo", consultasRs.getInt("consecutivo"));
                        
                        consultasNode.add(consultaNode);
                    }
    
                    if (consultasNode.size() > 0) {
                        serviciosNode.set("consultas", consultasNode);
                    }

                    // Array de procedimientos
                    String procedimientosQuery = "SELECT P.codPrestador, P.fechaInicioAtencion, P.idMIPRES, P.numAutorizacion,\r\n" + //
                                                "P.codProcedimiento, P.viaingresoServicioSalud, P.modalidadGrupoServicioTecSal,\r\n" + //
                                                "P.grupoServicios, P.codServicio, P.finalidadTecnologiaSalud,\r\n" + //
                                                "P.tipoDocumentoIdentificacion, P.numDocumentoIdentificacion, \r\n" + //
                                                "P.codDiagnosticoPrincipal, P.codDiagnosticoRelacionado, P.codComplicacion,\r\n" + //
                                                "P.vrServicio, P.tipoPagoModerador, P.valorPagoModerador, P.numFEVPagoModerador,\r\n" + //
                                                "P.consecutivo FROM dbo.Rips_Procedimientos P\r\n" + //
                                                "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=P.IdRips_Usuario\n" + //
                                                "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" + //
                                                "WHERE P.IdRips_Usuario = ?";

                    PreparedStatement procedimientosStmt = conn.prepareStatement(procedimientosQuery);
                    procedimientosStmt.setInt(1, idRipsUsuario);
                    ResultSet procedimientosRs = procedimientosStmt.executeQuery();

                    ArrayNode procedimientosNode = mapper.createArrayNode();
                    while (procedimientosRs.next()) {
                    ObjectNode procedimientoNode = mapper.createObjectNode();
                    procedimientoNode.put("codPrestador", procedimientosRs.getString("codPrestador"));

                    Timestamp fechaInicioAtencionTimestamp = procedimientosRs.getTimestamp("fechaInicioAtencion");
                    if (fechaInicioAtencionTimestamp != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm");
                    String fechaInicioAtencionStr = sdf.format(fechaInicioAtencionTimestamp);
                    procedimientoNode.put("fechaInicioAtencion", fechaInicioAtencionStr);
                    }

                    procedimientoNode.put("idMIPRES", procedimientosRs.getString("idMIPRES"));
                    procedimientoNode.put("numAutorizacion", procedimientosRs.getString("numAutorizacion"));
                    procedimientoNode.put("codProcedimiento", procedimientosRs.getString("codProcedimiento"));
                    procedimientoNode.put("viaIngresoServicioSalud", procedimientosRs.getString("viaIngresoServicioSalud"));
                    procedimientoNode.put("modalidadGrupoServicioTecSal", procedimientosRs.getString("modalidadGrupoServicioTecSal"));
                    procedimientoNode.put("grupoServicios", procedimientosRs.getString("grupoServicios"));
                    procedimientoNode.put("codServicio", procedimientosRs.getInt("codServicio"));
                    procedimientoNode.put("finalidadTecnologiaSalud", procedimientosRs.getString("finalidadTecnologiaSalud"));
                    procedimientoNode.put("tipoDocumentoIdentificacion", procedimientosRs.getString("tipoDocumentoIdentificacion"));
                    procedimientoNode.put("numDocumentoIdentificacion", procedimientosRs.getString("numDocumentoIdentificacion"));
                    procedimientoNode.put("codDiagnosticoPrincipal", procedimientosRs.getString("codDiagnosticoPrincipal"));
                    procedimientoNode.put("codDiagnosticoRelacionado", procedimientosRs.getString("codDiagnosticoRelacionado"));
                    procedimientoNode.put("codComplicacion", procedimientosRs.getString("codComplicacion"));
                    procedimientoNode.put("vrServicio", procedimientosRs.getInt("vrServicio"));
                    procedimientoNode.put("conceptoRecaudo", procedimientosRs.getString("tipoPagoModerador"));
                    procedimientoNode.put("valorPagoModerador", procedimientosRs.getInt("valorPagoModerador"));
                    procedimientoNode.put("numFEVPagoModerador", procedimientosRs.getString("numFEVPagoModerador"));
                    procedimientoNode.put("consecutivo", procedimientosRs.getInt("consecutivo"));

                    procedimientosNode.add(procedimientoNode);
                    }

                    if (procedimientosNode.size() > 0) {
                    serviciosNode.set("procedimientos", procedimientosNode);
                    }

                    // Array de urgencias
                    String urgenciasQuery = "SELECT UR.codPrestador, UR.fechaInicioAtencion, UR.causaMotivoAtencion, UR.codDiagnosticoPrincipal, \n" + //
                                                "UR.codDiagnosticoPrincipalE, UR.codDiagnosticoRelacionadoE1, UR.codDiagnosticoRelacionadoE2, \n" + //
                                                "UR.codDiagnosticoRelacionadoE3, UR.condicionDestinoUsuarioEgreso, UR.codDiagnosticoCausaMuerte, \n" + //
                                                "UR.fechaEgreso, UR.consecutivo FROM dbo.Rips_Urg UR\n" + //
                                                "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=UR.IdRips_Usuario\n" + //
                                                "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" + //
                                                "WHERE UR.IdRips_Usuario = ?";

                    PreparedStatement urgenciasStmt = conn.prepareStatement(urgenciasQuery);
                    urgenciasStmt.setInt(1, idRipsUsuario);
                    ResultSet urgenciasRs = urgenciasStmt.executeQuery();
    
                    ArrayNode urgenciasNode = mapper.createArrayNode();
                    while (urgenciasRs.next()) {
                        ObjectNode urgenciaNode = mapper.createObjectNode();
                        urgenciaNode.put("codPrestador", urgenciasRs.getString("codPrestador"));
    
                        Timestamp fechaInicioAtencionTimestamp = urgenciasRs.getTimestamp("fechaInicioAtencion");
                        if (fechaInicioAtencionTimestamp != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm");
                            String fechaInicioAtencionStr = sdf.format(fechaInicioAtencionTimestamp);
                            urgenciaNode.put("fechaInicioAtencion", fechaInicioAtencionStr);
                        }
    
                        urgenciaNode.put("causaMotivoAtencion", urgenciasRs.getString("causaMotivoAtencion"));
                        urgenciaNode.put("codDiagnosticoPrincipal", urgenciasRs.getString("codDiagnosticoPrincipal"));
                        urgenciaNode.put("codDiagnosticoPrincipalE", urgenciasRs.getString("codDiagnosticoPrincipalE"));
                        urgenciaNode.put("codDiagnosticoRelacionadoE1", urgenciasRs.getString("codDiagnosticoRelacionadoE1"));
                        urgenciaNode.put("codDiagnosticoRelacionadoE2", urgenciasRs.getString("codDiagnosticoRelacionadoE2"));
                        urgenciaNode.put("codDiagnosticoRelacionadoE3", urgenciasRs.getString("codDiagnosticoRelacionadoE3"));
                        urgenciaNode.put("condicionDestinoUsuarioEgreso", urgenciasRs.getString("condicionDestinoUsuarioEgreso")); //aqui va 01
                        urgenciaNode.put("codDiagnosticoCausaMuerte", urgenciasRs.getString("codDiagnosticoCausaMuerte"));
    
                        Timestamp fechaEgresoTimestamp = urgenciasRs.getTimestamp("fechaEgreso");
                        if (fechaEgresoTimestamp != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm");
                            String fechaEgresoStr = sdf.format(fechaEgresoTimestamp);
                            urgenciaNode.put("fechaEgreso", fechaEgresoStr);
                        }
                        
                        urgenciaNode.put("consecutivo", urgenciasRs.getInt("consecutivo"));
                        urgenciasNode.add(urgenciaNode);
                    }
    
                    if (urgenciasNode.size() > 0) {
                        serviciosNode.set("urgencias", urgenciasNode);
                    }
    
                    // Nuevo array de hospitalizaciones
                    String hospitalizacionesQuery = "SELECT H.codPrestador, H.viaingresoServicioSalud, H.fechaInicioAtencion, H.numAutorizacion,\n" + //
                                                "H.causaMotivoAtencion, H.codDiagnosticoPrincipal, H.codDiagnosticoPrincipalE,\n" + //
                                                "H.codDiagnosticoRelacionadoE1, H.codDiagnosticoRelacionadoE2, \n" + //
                                                "H.codDiagnosticoRelacionadoE3, H.codComplicacion, H.condicionDestinoUsuarioEgreso, \n" + //
                                                "H.codDiagnosticoCausaMuerte, H.fechaEgreso, H.consecutivo FROM dbo.Rips_Hospitalizacion H\n" + //
                                                "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=H.IdFRips_Usuario\n" + //
                                                "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" + //
                                                "WHERE H.IdFrips_Usuario = ?";

                    PreparedStatement hospitalizacionesStmt = conn.prepareStatement(hospitalizacionesQuery);
                    hospitalizacionesStmt.setInt(1, idRipsUsuario);
                    ResultSet hospitalizacionesRs = hospitalizacionesStmt.executeQuery();
    
                    ArrayNode hospitalizacionesNode = mapper.createArrayNode();
                    while (hospitalizacionesRs.next()) {
                        ObjectNode hospitalizacionNode = mapper.createObjectNode();
                        hospitalizacionNode.put("codPrestador", hospitalizacionesRs.getString("codPrestador"));
                        hospitalizacionNode.put("viaIngresoServicioSalud", hospitalizacionesRs.getString("viaingresoServicioSalud"));
    
                        // Manejo del campo DATETIME fechaInicioAtencion
                        Timestamp fechaInicioAtencionTimestamp = hospitalizacionesRs.getTimestamp("fechaInicioAtencion");
                        if (fechaInicioAtencionTimestamp != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm"); // Formato deseado
                            String fechaInicioAtencionStr = sdf.format(fechaInicioAtencionTimestamp);
                            hospitalizacionNode.put("fechaInicioAtencion", fechaInicioAtencionStr);
                        }
    
                        hospitalizacionNode.put("numAutorizacion", hospitalizacionesRs.getString("numAutorizacion"));
                        hospitalizacionNode.put("causaMotivoAtencion", hospitalizacionesRs.getString("causaMotivoAtencion"));
                        hospitalizacionNode.put("codDiagnosticoPrincipal", hospitalizacionesRs.getString("codDiagnosticoPrincipal"));
                        hospitalizacionNode.put("codDiagnosticoPrincipalE", hospitalizacionesRs.getString("codDiagnosticoPrincipalE"));
                        hospitalizacionNode.put("codDiagnosticoRelacionadoE1", hospitalizacionesRs.getString("codDiagnosticoRelacionadoE1"));
                        hospitalizacionNode.put("codDiagnosticoRelacionadoE2", hospitalizacionesRs.getString("codDiagnosticoRelacionadoE2"));
                        hospitalizacionNode.put("codDiagnosticoRelacionadoE3", hospitalizacionesRs.getString("codDiagnosticoRelacionadoE3"));
                        hospitalizacionNode.put("codComplicacion", hospitalizacionesRs.getString("codComplicacion"));
                        hospitalizacionNode.put("condicionDestinoUsuarioEgreso", hospitalizacionesRs.getString("condicionDestinoUsuarioEgreso"));
                        hospitalizacionNode.put("codDiagnosticoCausaMuerte", hospitalizacionesRs.getString("codDiagnosticoCausaMuerte"));
    
                        // Manejo del campo DATETIME fechaEgreso
                        Timestamp fechaEgresoTimestamp = hospitalizacionesRs.getTimestamp("fechaEgreso");
                        if (fechaEgresoTimestamp != null) {
                            SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm"); // Formato deseado
                            String fechaEgresoStr = sdf2.format(fechaEgresoTimestamp);
                            hospitalizacionNode.put("fechaEgreso", fechaEgresoStr);
                        }
    
                        hospitalizacionNode.put("consecutivo", hospitalizacionesRs.getInt("consecutivo"));
                        hospitalizacionesNode.add(hospitalizacionNode);
                    }
                    if (hospitalizacionesNode.size() > 0) {
                        serviciosNode.set("hospitalizacion", hospitalizacionesNode);
                    }
    
                    // Nuevo array de recién nacidos
                    String recienNacidosQuery = "SELECT RN.codPrestador, RN.tipoDocumentoIdentificacion, RN.numDocumentoIdentificacion, RN.fechaNacimiento, \n" + //
                                                "RN.edadGestacional, RN.numConsultasCPrenatal, RN.codSexoBiologico, RN.peso, RN.codDiagnosticoPrincipal, \n" + //
                                                "RN.condicionDestinoUsuarioEgreso, RN.codDiagnosticoCausaMuerte, RN.fechaEgreso, RN.consecutivo FROM dbo.Rips_RecienNacidos RN\n" + //
                                                "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=RN.IdRips_Usuario\n" + //
                                                "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" + //
                                                "WHERE RN.IdRips_Usuario = ?";
                    PreparedStatement recienNacidosStmt = conn.prepareStatement(recienNacidosQuery);
                    recienNacidosStmt.setInt(1, idRipsUsuario);
                    ResultSet recienNacidosRs = recienNacidosStmt.executeQuery();
    
                    ArrayNode recienNacidosNode = mapper.createArrayNode();
                    while (recienNacidosRs.next()) {
                        ObjectNode recienNacidoNode = mapper.createObjectNode();
                        recienNacidoNode.put("codPrestador", recienNacidosRs.getString("codPrestador"));
                        recienNacidoNode.put("tipoDocumentoIdentificacion", recienNacidosRs.getString("tipoDocumentoIdentificacion"));
                        recienNacidoNode.put("numDocumentoIdentificacion", recienNacidosRs.getString("numDocumentoIdentificacion"));
    
                        // Manejo del campo DATETIME fechaNacimiento
                        Timestamp fechaNacimientoTimestamp2 = recienNacidosRs.getTimestamp("fechaNacimiento");
                        if (fechaNacimientoTimestamp2 != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm"); // Formato deseado
                            String fechaNacimientoStr = sdf.format(fechaNacimientoTimestamp2);
                            recienNacidoNode.put("fechaNacimiento", fechaNacimientoStr);
                        }
    
                        recienNacidoNode.put("edadGestacional", recienNacidosRs.getInt("edadGestacional"));
                        recienNacidoNode.put("numConsultasCPrenatal", recienNacidosRs.getInt("numConsultasCPrenatal"));
                        recienNacidoNode.put("codSexoBiologico", recienNacidosRs.getString("codSexoBiologico"));
                        recienNacidoNode.put("peso", recienNacidosRs.getBigDecimal("peso"));
                        recienNacidoNode.put("codDiagnosticoPrincipal", recienNacidosRs.getString("codDiagnosticoPrincipal"));
                        recienNacidoNode.put("condicionDestinoUsuarioEgreso", recienNacidosRs.getString("condicionDestinoUsuarioEgreso"));
                        recienNacidoNode.put("codDiagnosticoCausaMuerte", recienNacidosRs.getString("codDiagnosticoCausaMuerte"));
    
                        // Manejo del campo DATETIME fechaEgreso
                        Timestamp fechaEgresoTimestamp = recienNacidosRs.getTimestamp("fechaEgreso");
                        if (fechaEgresoTimestamp != null) {
                            SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm"); // Formato deseado
                            String fechaEgresoStr = sdf2.format(fechaEgresoTimestamp);
                            recienNacidoNode.put("fechaEgreso", fechaEgresoStr);
                        }
    
                        recienNacidoNode.put("consecutivo", recienNacidosRs.getInt("consecutivo"));
                        recienNacidosNode.add(recienNacidoNode);
                    }
    
                    if (recienNacidosNode.size() > 0) {
                        serviciosNode.set("recienNacidos", recienNacidosNode);
                    }

                    // Nuevo array de medicamentos
                    String medicamentosQuery = "SELECT M.codPrestador, M.numAutorizacion, M.idMIPRES, M.fechaDispensAdmon, M.codDiagnosticoPrincipal, \n" + //
                                                "M.codDiagnosticoRelacionado, M.tipoMedicamento, M.codTecnologiaSalud, M.nomTecnologiaSalud, \n" + //
                                                "M.concentracionMedicamento, M.unidadMedida, M.formaFarmaceutica, M.unidadMinDispensa, \n" + //
                                                "M.cantidadMedicamento, M.diasTratamiento, M.tipoDocumentoIdentificacion, M.numDocumentoidentificacion, \n" + //
                                                "M.vrUnitMedicamento, M.vrServicio, M.tipoPagoModerador, M.valorPagoModerador, M.numFEVPagoModerador, \n" + //
                                                "M.consecutivo FROM dbo.Rips_Medicamentos M\n" + //
                                                "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=M.IdRips_Usuario\n" + //
                                                "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" + //
                                                "WHERE M.IdRips_Usuario = ?";

                    PreparedStatement medicamentosStmt = conn.prepareStatement(medicamentosQuery);
                    medicamentosStmt.setInt(1, idRipsUsuario);
                    ResultSet medicamentosRs = medicamentosStmt.executeQuery();

                    ArrayNode medicamentosNode = mapper.createArrayNode();
                    while (medicamentosRs.next()) {
                        ObjectNode medicamentoNode = mapper.createObjectNode();
                        medicamentoNode.put("codPrestador", medicamentosRs.getString("codPrestador"));
                        medicamentoNode.put("numAutorizacion", medicamentosRs.getString("numAutorizacion"));
                        medicamentoNode.put("idMIPRES", medicamentosRs.getString("idMIPRES"));

                        // Manejo del campo DATETIME fechaDispensAdmon
                        Timestamp fechaDispensAdmonTimestamp = medicamentosRs.getTimestamp("fechaDispensAdmon");
                        if (fechaDispensAdmonTimestamp != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm"); // Formato deseado
                            String fechaDispensAdmonStr = sdf.format(fechaDispensAdmonTimestamp);
                            medicamentoNode.put("fechaDispensAdmon", fechaDispensAdmonStr);
                        }

                        medicamentoNode.put("codDiagnosticoPrincipal", medicamentosRs.getString("codDiagnosticoPrincipal"));
                        medicamentoNode.put("codDiagnosticoRelacionado", medicamentosRs.getString("codDiagnosticoRelacionado"));
                        medicamentoNode.put("tipoMedicamento", medicamentosRs.getString("tipoMedicamento"));
                        medicamentoNode.put("codTecnologiaSalud", medicamentosRs.getString("codTecnologiaSalud"));
                        medicamentoNode.put("nomTecnologiaSalud", medicamentosRs.getString("nomTecnologiaSalud"));
                        medicamentoNode.put("concentracionMedicamento", medicamentosRs.getInt("concentracionMedicamento"));
                        medicamentoNode.put("unidadMedida", medicamentosRs.getInt("unidadMedida"));
                        medicamentoNode.put("formaFarmaceutica", medicamentosRs.getString("formaFarmaceutica"));
                        medicamentoNode.put("unidadMinDispensa", medicamentosRs.getInt("unidadMinDispensa"));
                        medicamentoNode.put("cantidadMedicamento", medicamentosRs.getBigDecimal("cantidadMedicamento"));
                        medicamentoNode.put("diasTratamiento", medicamentosRs.getInt("diasTratamiento"));
                        medicamentoNode.put("tipoDocumentoIdentificacion", medicamentosRs.getString("tipoDocumentoIdentificacion"));
                        medicamentoNode.put("numDocumentoIdentificacion", medicamentosRs.getString("numDocumentoidentificacion"));
                        medicamentoNode.put("vrUnitMedicamento", medicamentosRs.getBigDecimal("vrUnitMedicamento"));
                        medicamentoNode.put("vrServicio", medicamentosRs.getBigDecimal("vrServicio"));
                        medicamentoNode.put("conceptoRecaudo", medicamentosRs.getString("tipoPagoModerador")); //REVISAR ESTO
                        medicamentoNode.put("valorPagoModerador", medicamentosRs.getInt("valorPagoModerador"));
                        medicamentoNode.put("numFEVPagoModerador", medicamentosRs.getString("numFEVPagoModerador"));
                        medicamentoNode.put("consecutivo", medicamentosRs.getInt("consecutivo"));
                        medicamentosNode.add(medicamentoNode);
                    }

                    if (medicamentosNode.size() > 0) {
                        serviciosNode.set("medicamentos", medicamentosNode);
                    }

                    // Nuevo array de otros servicios
                    String otroServiciosQuery = "SELECT OS.codPrestador, OS.numAutorizacion, OS.idMIPRES, OS.fechaSuministroTecnologia, OS.tipoOS, \n" + //
                                                "OS.codTecnologiaSalud, OS.nomTecnologiaSalud, OS.cantidadOS, OS.tipoDocumentoIdentificacion, \n" + //
                                                "OS.numDocumentoIdentificacion, OS.vrUnitOS, OS.vrServicio, OS.conceptoRecaudo, OS.valorPagoModerador, \n" + //
                                                "OS.numFEVPagoModerador, OS.consecutivo FROM dbo.Rips_OtrosServicios OS\n" + //
                                                "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=OS.IdRips_Usuario\n" + //
                                                "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" + //
                                                "WHERE OS.IdRips_Usuario = ?";

                    PreparedStatement otroServiciosStmt = conn.prepareStatement(otroServiciosQuery);
                    otroServiciosStmt.setInt(1, idRipsUsuario);
                    ResultSet otroServiciosRs = otroServiciosStmt.executeQuery();

                    ArrayNode otroServiciosNode = mapper.createArrayNode();
                    while (otroServiciosRs.next()) {
                        ObjectNode otroServicioNode = mapper.createObjectNode();
                        otroServicioNode.put("codPrestador", otroServiciosRs.getString("codPrestador"));
                        otroServicioNode.put("numAutorizacion", otroServiciosRs.getString("numAutorizacion"));
                        otroServicioNode.put("idMIPRES", otroServiciosRs.getString("idMIPRES"));

                        // Manejo del campo DATETIME fechaSuministroTecnologia
                        Timestamp fechaSuministroTecnologiaTimestamp = otroServiciosRs.getTimestamp("fechaSuministroTecnologia");
                        if (fechaSuministroTecnologiaTimestamp != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm"); // Formato deseado
                            String fechaSuministroTecnologiaStr = sdf.format(fechaSuministroTecnologiaTimestamp);
                            otroServicioNode.put("fechaSuministroTecnologia", fechaSuministroTecnologiaStr);
                        }

                        otroServicioNode.put("tipoOS", otroServiciosRs.getString("tipoOS"));
                        otroServicioNode.put("codTecnologiaSalud", otroServiciosRs.getString("codTecnologiaSalud"));
                        otroServicioNode.put("nomTecnologiaSalud", otroServiciosRs.getString("nomTecnologiaSalud"));
                        otroServicioNode.put("cantidadOS", otroServiciosRs.getInt("cantidadOS"));
                        otroServicioNode.put("tipoDocumentoIdentificacion", otroServiciosRs.getString("tipoDocumentoIdentificacion"));
                        otroServicioNode.put("numDocumentoIdentificacion", otroServiciosRs.getString("numDocumentoIdentificacion"));
                        otroServicioNode.put("vrUnitOS", otroServiciosRs.getBigDecimal("vrUnitOS"));
                        otroServicioNode.put("vrServicio", otroServiciosRs.getBigDecimal("vrServicio"));
                        otroServicioNode.put("conceptoRecaudo", otroServiciosRs.getString("conceptoRecaudo"));
                        otroServicioNode.put("valorPagoModerador", otroServiciosRs.getInt("valorPagoModerador"));
                        otroServicioNode.put("numFEVPagoModerador", otroServiciosRs.getString("numFEVPagoModerador")); //REVISAR
                        otroServicioNode.put("consecutivo", otroServiciosRs.getInt("consecutivo"));
                        otroServiciosNode.add(otroServicioNode);
                    }

                    if (otroServiciosNode.size() > 0) {
                        serviciosNode.set("otrosServicios", otroServiciosNode);
                    }

                    if (serviciosNode.size() > 0) {
                        usuarioNode.set("servicios", serviciosNode);
                        usuariosNode.add(usuarioNode); // Agregar solo si hay servicios
                    }
                }
            }
            resultado.set("usuarios", usuariosNode);
        
                    // Verificar si el array de usuarios está vacío
            if (usuariosNode.size() == 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No se encontraron datos en Usuarios".getBytes(StandardCharsets.UTF_8));
            }
            
            // Retornar el JSON como byte[]
            return ResponseEntity.ok(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado).getBytes(StandardCharsets.UTF_8));
    
        } catch (SQLException e) {
            System.err.println("Error al ejecutar las consultas SQL: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Error al ejecutar las consultas SQL: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("Error al convertir a JSON: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Error al convertir a JSON: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("Error al cerrar la conexión: " + e.getMessage());
                }
            }
        }
    }

    @GetMapping("/generartxt/{idMovDoc}")
    public ResponseEntity<Map<String, byte[]>> generarTxt(@PathVariable int idMovDoc) {
        Connection conn = null;
        Map<String, byte[]> txtFiles = new HashMap<>();
    
        try {
            // Obtener el nombre del servidor desde el registro
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format("jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;", servidor);
            conn = DriverManager.getConnection(connectionUrl);
    
            // 1. Consulta de Facturas
            StringBuilder sbFacturas = new StringBuilder();
            String facturasQuery = "SELECT IdEmpresaGrupo, NFact, tipoNota, numNota FROM dbo.Rips_Transaccion WHERE IdMovDoc = ?";
            PreparedStatement pstmt = conn.prepareStatement(facturasQuery);
            pstmt.setInt(1, idMovDoc);
            ResultSet facturasRs = pstmt.executeQuery();
            while (facturasRs.next()) {
                sbFacturas.append(String.format("%s,%s,%s,%s\n",
                        facturasRs.getString("IdEmpresaGrupo"),
                        facturasRs.getString("NFact"),
                        facturasRs.getString("tipoNota"),
                        facturasRs.getString("numNota")));
            }
            if (sbFacturas.length() > 0) {
                txtFiles.put("transaccion.txt", sbFacturas.toString().getBytes(StandardCharsets.UTF_8));
            }
    
            // 2. Consulta de Usuarios
            StringBuilder sbUsuarios = new StringBuilder();
            String usuariosQuery = "SELECT tipoDocumentoIdentificacion, numDocumentoIdentificacion, tipoUsuario, fechaNacimiento, codSexo, CodPaisResidencia, ResHabitual, codZonaTerritorialResidencia, incapacidad, consecutivo, codPaisOrigen FROM dbo.Rips_Usuarios INNER JOIN dbo.Rips_Transaccion ON dbo.Rips_Transaccion.IdRips=dbo.Rips_Usuarios.IdRips WHERE dbo.Rips_Transaccion.IdMovDoc = ?";
            PreparedStatement usuariosStmt = conn.prepareStatement(usuariosQuery);
            usuariosStmt.setInt(1, idMovDoc);
            ResultSet usuariosRs = usuariosStmt.executeQuery();
            while (usuariosRs.next()) {
                sbUsuarios.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%s\n",
                        usuariosRs.getString("tipoDocumentoIdentificacion"),
                        usuariosRs.getString("numDocumentoIdentificacion"),
                        usuariosRs.getString("tipoUsuario"),
                        usuariosRs.getTimestamp("fechaNacimiento") != null ? new SimpleDateFormat("yyyy-MM-dd").format(usuariosRs.getTimestamp("fechaNacimiento")) : null,
                        usuariosRs.getString("codSexo"),
                        usuariosRs.getString("CodPaisResidencia"),
                        usuariosRs.getString("ResHabitual"),
                        usuariosRs.getString("codZonaTerritorialResidencia"),
                        usuariosRs.getString("incapacidad"),
                        usuariosRs.getInt("consecutivo"),
                        usuariosRs.getString("codPaisOrigen")));
            }
            if (sbUsuarios.length() > 0) {
                txtFiles.put("usuarios.txt", sbUsuarios.toString().getBytes(StandardCharsets.UTF_8));
            }

            // 3. Consulta de Consultas Médicas
            StringBuilder sbConsultas = new StringBuilder();
            String consultasQuery = "SELECT C.codPrestador, C.fechaInicioAtencion, C.numAutorizacion, C.codConsulta, C.modalidadGrupoServicioTecSal, C.grupoServicios, C.codServicio\n" +
                    ", C.finalidadTecnologiaSalud, C.causaMotivoAtencion, C.codDiagnosticoPrincipal, C.codDiagnosticoRelacionado1, C.codDiagnosticoRelacionado2\n" +
                    ", C.codDiagnosticoRelacionado3, C.tipoDiagnosticoPrincipal, C.tipoDocumentoIdentificacion, C.numDocumentoIdentificacion, C.vrServicio\n" +
                    ", C.tipoPagoModerador, C.valorPagoModerador, C.numFEVPagoModerador, C.consecutivo \n" +
                    "FROM dbo.Rips_Consulta C\n" +
                    "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=C.IdRips_Usuario\n" +
                    "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" +
                    "WHERE T.IdMovDoc = ?";

            PreparedStatement consultasStmt = conn.prepareStatement(consultasQuery);
            consultasStmt.setInt(1, idMovDoc);
            ResultSet consultasRs = consultasStmt.executeQuery();

            while (consultasRs.next()) {
                sbConsultas.append(String.format("%s,%s,%s,%s,%s,%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%s,%d,%s,%d\n",
                        consultasRs.getString("codPrestador"),
                        consultasRs.getTimestamp("fechaInicioAtencion") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(consultasRs.getTimestamp("fechaInicioAtencion")) : null,
                        consultasRs.getString("numAutorizacion"),
                        consultasRs.getString("codConsulta"),
                        consultasRs.getString("modalidadGrupoServicioTecSal"),
                        consultasRs.getString("grupoServicios"),
                        consultasRs.getInt("codServicio"),
                        consultasRs.getString("finalidadTecnologiaSalud"),
                        consultasRs.getString("causaMotivoAtencion"),
                        consultasRs.getString("codDiagnosticoPrincipal"),
                        consultasRs.getString("codDiagnosticoRelacionado1"),
                        consultasRs.getString("codDiagnosticoRelacionado2"),
                        consultasRs.getString("codDiagnosticoRelacionado3"),
                        consultasRs.getString("tipoDiagnosticoPrincipal"),
                        consultasRs.getString("tipoDocumentoIdentificacion"),
                        consultasRs.getString("numDocumentoIdentificacion"),
                        consultasRs.getInt("vrServicio"),
                        consultasRs.getString("tipoPagoModerador"),
                        consultasRs.getInt("valorPagoModerador"),
                        consultasRs.getString("numFEVPagoModerador"),
                        consultasRs.getInt("consecutivo")));
            }

            // Agregar el contenido de Consultas Médicas al mapa
            if (sbConsultas.length() > 0) {
                txtFiles.put("consultas.txt", sbConsultas.toString().getBytes(StandardCharsets.UTF_8));
            }
            

            // 4. Consulta de Procedimientos
            StringBuilder sbProcedimientos = new StringBuilder();
            String procedimientosQuery = "SELECT P.codPrestador, P.fechaInicioAtencion, P.idMIPRES, P.numAutorizacion, P.codProcedimiento, P.viaIngresoServicioSalud, P.modalidadGrupoServicioTecSal, P.grupoServicios, P.codServicio, P.finalidadTecnologiaSalud, P.tipoDocumentoIdentificacion, P.numDocumentoIdentificacion, P.codDiagnosticoPrincipal, P.codDiagnosticoRelacionado, P.codComplicacion, P.vrServicio, P.tipoPagoModerador, P.valorPagoModerador, P.numFEVPagoModerador, P.consecutivo FROM dbo.Rips_Procedimientos P INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=P.IdRips_Usuario INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips WHERE T.IdMovDoc = ?";

            PreparedStatement procedimientosStmt = conn.prepareStatement(procedimientosQuery);
            procedimientosStmt.setInt(1, idMovDoc);
            ResultSet procedimientosRs = procedimientosStmt.executeQuery();

            while (procedimientosRs.next()) {
                sbProcedimientos.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%d,%s,%s,%s,%s,%s,%s,%d,%s,%d,%s,%d\n",
                        procedimientosRs.getString("codPrestador"),
                        procedimientosRs.getTimestamp("fechaInicioAtencion") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(procedimientosRs.getTimestamp("fechaInicioAtencion")) : null,
                        procedimientosRs.getString("idMIPRES"),
                        procedimientosRs.getString("numAutorizacion"),
                        procedimientosRs.getString("codProcedimiento"),
                        procedimientosRs.getString("viaIngresoServicioSalud"),                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          
                        procedimientosRs.getString("modalidadGrupoServicioTecSal"),
                        procedimientosRs.getString("grupoServicios"),
                        procedimientosRs.getInt("codServicio"),
                        procedimientosRs.getString("finalidadTecnologiaSalud"),
                        procedimientosRs.getString("tipoDocumentoIdentificacion"),
                        procedimientosRs.getString("numDocumentoIdentificacion"),
                        procedimientosRs.getString("codDiagnosticoPrincipal"),
                        procedimientosRs.getString("codDiagnosticoRelacionado"),
                        procedimientosRs.getString("codComplicacion"),
                        procedimientosRs.getInt("vrServicio"),
                        procedimientosRs.getString("tipoPagoModerador"),
                        procedimientosRs.getInt("valorPagoModerador"),
                        procedimientosRs.getString("numFEVPagoModerador"),
                        procedimientosRs.getInt("consecutivo")));
            }

            // Agregar el contenido de Procedimientos al mapa
            if (sbProcedimientos.length() > 0) {
                txtFiles.put("procedimientos.txt", sbProcedimientos.toString().getBytes(StandardCharsets.UTF_8));
            }


            // 5. Consulta de Urgencias
            StringBuilder sbUrgencias = new StringBuilder();
            String urgenciasQuery = "SELECT UR.codPrestador, UR.fechaInicioAtencion, UR.causaMotivoAtencion, UR.codDiagnosticoPrincipal, UR.codDiagnosticoPrincipalE, UR.codDiagnosticoRelacionadoE1, UR.codDiagnosticoRelacionadoE2, UR.codDiagnosticoRelacionadoE3, UR.condicionDestinoUsuarioEgreso, UR.codDiagnosticoCausaMuerte, UR.fechaEgreso, UR.consecutivo FROM dbo.Rips_Urg UR INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=UR.IdRips_Usuario INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips WHERE T.IdMovDoc = ?";

            PreparedStatement urgenciasStmt = conn.prepareStatement(urgenciasQuery);
            urgenciasStmt.setInt(1, idMovDoc);
            ResultSet urgenciasRs = urgenciasStmt.executeQuery();

            while (urgenciasRs.next()) {
                sbUrgencias.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%d\n",
                        urgenciasRs.getString("codPrestador"),
                        urgenciasRs.getTimestamp("fechaInicioAtencion") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(urgenciasRs.getTimestamp("fechaInicioAtencion")) : null,
                        urgenciasRs.getString("causaMotivoAtencion"),
                        urgenciasRs.getString("codDiagnosticoPrincipal"),
                        urgenciasRs.getString("codDiagnosticoPrincipalE"),
                        urgenciasRs.getString("codDiagnosticoRelacionadoE1"),
                        urgenciasRs.getString("codDiagnosticoRelacionadoE2"),
                        urgenciasRs.getString("codDiagnosticoRelacionadoE3"),
                        urgenciasRs.getString("condicionDestinoUsuarioEgreso"),
                        urgenciasRs.getString("codDiagnosticoCausaMuerte"),
                        urgenciasRs.getTimestamp("fechaEgreso") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(urgenciasRs.getTimestamp("fechaEgreso")) : null,
                        urgenciasRs.getInt("consecutivo")));
            }

            // Agregar el contenido de Urgencias al mapa
            if (sbUrgencias.length() > 0) {
                txtFiles.put("urgencias.txt", sbUrgencias.toString().getBytes(StandardCharsets.UTF_8));
            }

            // 6. Consulta de Hospitalizaciones
            StringBuilder sbHospitalizaciones = new StringBuilder();
            String hospitalizacionesQuery = "SELECT H.codPrestador, H.viaingresoServicioSalud, H.fechaInicioAtencion, H.numAutorizacion,\n" +
                    "H.causaMotivoAtencion, H.codDiagnosticoPrincipal, H.codDiagnosticoPrincipalE,\n" +
                    "H.codDiagnosticoRelacionadoE1, H.codDiagnosticoRelacionadoE2, \n" +
                    "H.codDiagnosticoRelacionadoE3, H.codComplicacion, H.condicionDestinoUsuarioEgreso, \n" +
                    "H.codDiagnosticoCausaMuerte, H.fechaEgreso, H.consecutivo FROM dbo.Rips_Hospitalizacion H\n" +
                    "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=H.IdFRips_Usuario\n" +
                    "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" +
                    "WHERE T.IdMovDoc = ?";

            PreparedStatement hospitalizacionesStmt = conn.prepareStatement(hospitalizacionesQuery);
            hospitalizacionesStmt.setInt(1, idMovDoc);
            ResultSet hospitalizacionesRs = hospitalizacionesStmt.executeQuery();

            while (hospitalizacionesRs.next()) {
                sbHospitalizaciones.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%d\n",
                        hospitalizacionesRs.getString("codPrestador"),
                        hospitalizacionesRs.getString("viaingresoServicioSalud"),
                        hospitalizacionesRs.getTimestamp("fechaInicioAtencion") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(hospitalizacionesRs.getTimestamp("fechaInicioAtencion")) : null,
                        hospitalizacionesRs.getString("numAutorizacion"),
                        hospitalizacionesRs.getString("causaMotivoAtencion"),
                        hospitalizacionesRs.getString("codDiagnosticoPrincipal"),
                        hospitalizacionesRs.getString("codDiagnosticoPrincipalE"),
                        hospitalizacionesRs.getString("codDiagnosticoRelacionadoE1"),
                        hospitalizacionesRs.getString("codDiagnosticoRelacionadoE2"),
                        hospitalizacionesRs.getString("codDiagnosticoRelacionadoE3"),
                        hospitalizacionesRs.getString("codComplicacion"),
                        hospitalizacionesRs.getString("condicionDestinoUsuarioEgreso"),
                        hospitalizacionesRs.getString("codDiagnosticoCausaMuerte"),
                        hospitalizacionesRs.getTimestamp("fechaEgreso") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(hospitalizacionesRs.getTimestamp("fechaEgreso")) : null,
                        hospitalizacionesRs.getInt("consecutivo")));
            }

            // Agregar el contenido de Hospitalizaciones al mapa
            if (sbHospitalizaciones.length() > 0) {
                txtFiles.put("hospitalizacion.txt", sbHospitalizaciones.toString().getBytes(StandardCharsets.UTF_8));
            }


            // 7. Consulta de Recién Nacidos
            StringBuilder sbRecienNacidos = new StringBuilder();
            String recienNacidosQuery = "SELECT RN.codPrestador, RN.tipoDocumentoIdentificacion, RN.numDocumentoIdentificacion, RN.fechaNacimiento, \n" +
                    "RN.edadGestacional, RN.numConsultasCPrenatal, RN.codSexoBiologico, RN.peso, RN.codDiagnosticoPrincipal, \n" +
                    "RN.condicionDestinoUsuarioEgreso, RN.codDiagnosticoCausaMuerte, RN.fechaEgreso, RN.consecutivo FROM dbo.Rips_RecienNacidos RN\n" +
                    "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=RN.IdRips_Usuario\n" +
                    "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" +
                    "WHERE T.IdMovDoc = ?";

            PreparedStatement recienNacidosStmt = conn.prepareStatement(recienNacidosQuery);
            recienNacidosStmt.setInt(1, idMovDoc);
            ResultSet recienNacidosRs = recienNacidosStmt.executeQuery();

            while (recienNacidosRs.next()) {
                sbRecienNacidos.append(String.format("%s,%s,%s,%s,%d,%d,%s,%s,%s,%s,%s,%s,%d\n",
                        recienNacidosRs.getString("codPrestador"),
                        recienNacidosRs.getString("tipoDocumentoIdentificacion"),
                        recienNacidosRs.getString("numDocumentoIdentificacion"),
                        recienNacidosRs.getTimestamp("fechaNacimiento") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(recienNacidosRs.getTimestamp("fechaNacimiento")) : null,
                        recienNacidosRs.getInt("edadGestacional"),
                        recienNacidosRs.getInt("numConsultasCPrenatal"),
                        recienNacidosRs.getString("codSexoBiologico"),
                        recienNacidosRs.getBigDecimal("peso"),
                        recienNacidosRs.getString("codDiagnosticoPrincipal"),
                        recienNacidosRs.getString("condicionDestinoUsuarioEgreso"),
                        recienNacidosRs.getString("codDiagnosticoCausaMuerte"),
                        recienNacidosRs.getTimestamp("fechaEgreso") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(recienNacidosRs.getTimestamp("fechaEgreso")) : null,
                        recienNacidosRs.getInt("consecutivo")));
            }

            // Agregar el contenido de Recién Nacidos al mapa
            if (sbRecienNacidos.length() > 0) {
                txtFiles.put("recienNacidos.txt", sbRecienNacidos.toString().getBytes(StandardCharsets.UTF_8));
            }

            // 8. Consulta de Medicamentos
            StringBuilder sbMedicamentos = new StringBuilder();
            String medicamentosQuery = "SELECT M.codPrestador, M.numAutorizacion, M.idMIPRES, M.fechaDispensAdmon, M.codDiagnosticoPrincipal, \n" +
                    "M.codDiagnosticoRelacionado, M.tipoMedicamento, M.codTecnologiaSalud, M.nomTecnologiaSalud, \n" +
                    "M.concentracionMedicamento, M.unidadMedida, M.formaFarmaceutica, M.unidadMinDispensa, \n" +
                    "M.cantidadMedicamento, M.diasTratamiento, M.tipoDocumentoIdentificacion, M.numDocumentoidentificacion, \n" +
                    "M.vrUnitMedicamento, M.vrServicio, M.tipoPagoModerador, M.valorPagoModerador, M.numFEVPagoModerador, \n" +
                    "M.consecutivo FROM dbo.Rips_Medicamentos M\n" +
                    "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=M.IdRips_Usuario\n" +
                    "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" +
                    "WHERE T.IdMovDoc = ?";

            PreparedStatement medicamentosStmt = conn.prepareStatement(medicamentosQuery);
            medicamentosStmt.setInt(1, idMovDoc);
            ResultSet medicamentosRs = medicamentosStmt.executeQuery();

            while (medicamentosRs.next()) {
                sbMedicamentos.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%d,%s,%d,%s,%d,%s,%s,%s,%s,%s,%d,%s,%d\n",
                        medicamentosRs.getString("codPrestador"),
                        medicamentosRs.getString("numAutorizacion"),
                        medicamentosRs.getString("idMIPRES"),
                        medicamentosRs.getTimestamp("fechaDispensAdmon") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(medicamentosRs.getTimestamp("fechaDispensAdmon")) : null,
                        medicamentosRs.getString("codDiagnosticoPrincipal"),
                        medicamentosRs.getString("codDiagnosticoRelacionado"),
                        medicamentosRs.getString("tipoMedicamento"),
                        medicamentosRs.getString("codTecnologiaSalud"),
                        medicamentosRs.getString("nomTecnologiaSalud"),
                        medicamentosRs.getInt("concentracionMedicamento"),
                        medicamentosRs.getInt("unidadMedida"),
                        medicamentosRs.getString("formaFarmaceutica"),
                        medicamentosRs.getInt("unidadMinDispensa"),
                        medicamentosRs.getBigDecimal("cantidadMedicamento"),
                        medicamentosRs.getInt("diasTratamiento"),
                        medicamentosRs.getString("tipoDocumentoIdentificacion"),
                        medicamentosRs.getString("numDocumentoidentificacion"),
                        medicamentosRs.getBigDecimal("vrUnitMedicamento"), 
                        medicamentosRs.getBigDecimal("vrServicio"), 
                        medicamentosRs.getString("tipoPagoModerador"),
                        medicamentosRs.getInt("valorPagoModerador"),
                        medicamentosRs.getString("numFEVPagoModerador"),
                        medicamentosRs.getInt("consecutivo")));
            }

            // Agregar el contenido de Medicamentos al mapa
            if (sbMedicamentos.length() > 0) {
                txtFiles.put("medicamentos.txt", sbMedicamentos.toString().getBytes(StandardCharsets.UTF_8));
            }

            // 9. Consulta de Otros Servicios
            StringBuilder sbOtrosServicios = new StringBuilder();
            String otroServiciosQuery = "SELECT OS.codPrestador, OS.numAutorizacion, OS.idMIPRES, OS.fechaSuministroTecnologia, OS.tipoOS, \n" +
                    "OS.codTecnologiaSalud, OS.nomTecnologiaSalud, OS.cantidadOS, OS.tipoDocumentoIdentificacion, \n" +
                    "OS.numDocumentoIdentificacion, OS.vrUnitOS, OS.vrServicio, OS.conceptoRecaudo, OS.valorPagoModerador, \n" +
                    "OS.numFEVPagoModerador, OS.consecutivo FROM dbo.Rips_OtrosServicios OS\n" +
                    "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=OS.IdRips_Usuario\n" +
                    "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" +
                    "WHERE T.IdMovDoc = ?";

            PreparedStatement otroServiciosStmt = conn.prepareStatement(otroServiciosQuery);
            otroServiciosStmt.setInt(1, idMovDoc);
            ResultSet otroServiciosRs = otroServiciosStmt.executeQuery();

            while (otroServiciosRs.next()) {
                sbOtrosServicios.append(String.format("%s,%s,%s,%s,%s,%s,%s,%d,%s,%s,%s,%s,%s,%d,%s,%d\n",
                        otroServiciosRs.getString("codPrestador"),
                        otroServiciosRs.getString("numAutorizacion"),
                        otroServiciosRs.getString("idMIPRES"),
                        otroServiciosRs.getTimestamp("fechaSuministroTecnologia") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(otroServiciosRs.getTimestamp("fechaSuministroTecnologia")) : null,
                        otroServiciosRs.getString("tipoOS"),
                        otroServiciosRs.getString("codTecnologiaSalud"),
                        otroServiciosRs.getString("nomTecnologiaSalud"),
                        otroServiciosRs.getInt("cantidadOS"),
                        otroServiciosRs.getString("tipoDocumentoIdentificacion"),
                        otroServiciosRs.getString("numDocumentoIdentificacion"),
                        otroServiciosRs.getBigDecimal("vrUnitOS"), 
                        otroServiciosRs.getBigDecimal("vrServicio"),
                        otroServiciosRs.getString("conceptoRecaudo"),
                        otroServiciosRs.getInt("valorPagoModerador"),
                        otroServiciosRs.getString("numFEVPagoModerador"),
                        otroServiciosRs.getInt("consecutivo")));
            }

            // Agregar el contenido de Otros Servicios al mapa
            if (sbOtrosServicios.length() > 0) {
                txtFiles.put("otros_servicios.txt", sbOtrosServicios.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (txtFiles.isEmpty()) {
                Map<String, byte[]> errorResponse = new HashMap<>();
                errorResponse.put("mensaje", "No se encontraron datos para el ID proporcionado".getBytes(StandardCharsets.UTF_8));
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(errorResponse);
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON) // Opcional: puede ser OCTET_STREAM si prefieres
                    .body(txtFiles);
    
        } catch (SQLException e) {
            Map<String, byte[]> errorResponse = new HashMap<>();
            errorResponse.put("error.txt", ("Error en la base de datos: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } catch (Exception e) {
            Map<String, byte[]> errorResponse = new HashMap<>();
            errorResponse.put("error.txt", ("Error al procesar la solicitud: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // Loggear el error si es necesario
                }
            }
        }
    }

    @GetMapping("/generarzip/{idMovDoc}/{tipoArchivo}")
    public ResponseEntity<ByteArrayResource> generarzip(
            @PathVariable int idMovDoc,
            @PathVariable String tipoArchivo) { // "json", "txt" o "ambos"

        
        if (!tipoArchivo.equalsIgnoreCase("json") &&
        !tipoArchivo.equalsIgnoreCase("txt") &&
        !tipoArchivo.equalsIgnoreCase("ambos")) {
        return ResponseEntity.badRequest().body(new ByteArrayResource("Tipo de archivo no válido. Debe ser 'json', 'txt' o 'ambos'".getBytes()));
        }
    
        Connection conn = null;
        String prefijo = "";
        String numdoc = "";
        String IdEmpresaGrupo = "";
    
        try {
            // Obtener el nombre del servidor desde el registro
            String servidor = getServerFromRegistry();
    
            String connectionUrl = String.format("jdbc:sqlserver://%s;databaseName=IPSoftFinanciero_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;", servidor);
            conn = DriverManager.getConnection(connectionUrl);
    
            // Obtener Prefijo y Numdoc
            String docQuery = "SELECT Prefijo, Numdoc FROM MovimientoDocumentos WHERE IdMovDoc = ?";
            try (PreparedStatement stmt = conn.prepareStatement(docQuery)) {
                stmt.setInt(1, idMovDoc);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    prefijo = rs.getString("Prefijo");
                    numdoc = rs.getString("Numdoc");
                }
            }
    
            // Obtener IdEmpresaGrupo
            String empresaQuery = "SELECT IdEmpresaGrupo FROM MovimientoDocumentos as M INNER JOIN Empresas as E ON e.IdEmpresaKey =m.IdEmpresaKey  WHERE IdMovDoc = ?";
            try (PreparedStatement stmt = conn.prepareStatement(empresaQuery)) {
                stmt.setInt(1, idMovDoc);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    IdEmpresaGrupo = rs.getString("IdEmpresaGrupo");
                }
            }
    
            // Obtener los dos últimos dígitos del año actual
            String yearSuffix = String.valueOf(LocalDate.now().getYear()).substring(2);
    
            // Asegurarse que NumDoc TENGA 8 DIGITOS
            String formattedNumdoc = String.format("%08d", Integer.parseInt(numdoc));
    
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                String folderName = "Fac_" + prefijo + numdoc + "/";
    
                // 1. Obtener y agregar XML (obligatorio)
                ResponseEntity<byte[]> xmlResponse = exportDocXml(idMovDoc); // El número de serie ya no es necesario
                if (xmlResponse.getStatusCode() == HttpStatus.OK && xmlResponse.getBody() != null && xmlResponse.getBody().length > 0) {
                    ZipEntry xmlEntry = new ZipEntry(folderName + "ad0" + IdEmpresaGrupo + "000" + yearSuffix + formattedNumdoc + ".xml");
                    zos.putNextEntry(xmlEntry);
                    zos.write(xmlResponse.getBody());
                    zos.closeEntry();
                } else {
                    String errorMessage = "No se pudo obtener el XML."; // Mensaje por defecto
                    if (xmlResponse.getBody() != null && xmlResponse.getBody().length > 0) {
                        errorMessage = new String(xmlResponse.getBody(), StandardCharsets.UTF_8);
                    }
                    // Devolvemos el mismo estado HTTP que exportDocXml para que el frontend lo interprete
                    return ResponseEntity.status(xmlResponse.getStatusCode())
                                .body(new ByteArrayResource(errorMessage.getBytes()));
                    }
    
                // 2. Obtener y agregar JSON (si se solicitó)
                boolean jsonRequired = "json".equals(tipoArchivo) || "ambos".equals(tipoArchivo);
                if (jsonRequired) {
                    ResponseEntity<byte[]> jsonResponse = generarjson(idMovDoc);
                    if (jsonResponse.getStatusCode() == HttpStatus.OK && jsonResponse.getBody() != null && jsonResponse.getBody().length > 0) {
                        ZipEntry jsonEntry = new ZipEntry(folderName + "RipsFac_" + prefijo + numdoc + ".json");
                        zos.putNextEntry(jsonEntry);
                        zos.write(jsonResponse.getBody());
                        zos.closeEntry();
                    } else {
                        System.out.println("No hay datos para generar JSON para IdMovDoc: " + idMovDoc);
                    }
                }
    
                // 3. Obtener y agregar TXTs (si se solicitó)
                boolean txtRequired = "txt".equals(tipoArchivo) || "ambos".equals(tipoArchivo);
                if (txtRequired) {
                    ResponseEntity<Map<String, byte[]>> txtResponse = generarTxt(idMovDoc);
                    if (txtResponse.getStatusCode() == HttpStatus.OK && txtResponse.getBody() != null && !txtResponse.getBody().isEmpty()) {
                        for (Map.Entry<String, byte[]> entry : txtResponse.getBody().entrySet()) {
                            ZipEntry txtEntry = new ZipEntry(folderName + entry.getKey());
                            zos.putNextEntry(txtEntry);
                            zos.write(entry.getValue());
                            zos.closeEntry();
                        }
                    } else {
                        System.out.println("No hay datos para generar archivos TXT para IdMovDoc: " + idMovDoc);
                    }
                }
            }
    
            // Devolver ZIP generado
            ByteArrayResource resource = new ByteArrayResource(baos.toByteArray());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Fac_" + prefijo + numdoc + ".zip")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
    
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ByteArrayResource(("Error interno al generar ZIP: " + e.getMessage()).getBytes()));
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }
    }
}




