package com.stockmaster.backend.predictive;

import com.stockmaster.backend.service.FlujoCajaIaService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.*;

class ExactitudPredictivaTest {
    record Par(double real, double estimado) {}
    record Metricas(double mae, double rmse, OptionalDouble mape, int n, int nMape) {}

    static Metricas medir(List<Par> pares) {
        if (pares.isEmpty()) throw new IllegalArgumentException("Sin observaciones");
        double abs = 0, cuadrados = 0, porcentaje = 0;
        int nMape = 0;
        for (Par p : pares) {
            double error = Math.abs(p.real() - p.estimado());
            abs += error;
            cuadrados += error * error;
            if (p.real() != 0) {
                porcentaje += error / Math.abs(p.real()) * 100;
                nMape++;
            }
        }
        return new Metricas(abs / pares.size(), Math.sqrt(cuadrados / pares.size()),
                nMape == 0 ? OptionalDouble.empty() : OptionalDouble.of(porcentaje / nMape),
                pares.size(), nMape);
    }

    @Test
    void prediccionPerfectaTieneErrorCero() {
        var m = medir(List.of(new Par(100,100), new Par(200,200)));
        assertEquals(0, m.mae());
        assertEquals(0, m.rmse());
        assertEquals(0, m.mape().orElseThrow());
    }

    @Test
    void erroresConocidosProducenMetricasCorrectas() {
        var m = medir(List.of(new Par(100,90), new Par(200,220)));
        assertEquals(15, m.mae(), 1e-9);
        assertEquals(Math.sqrt(250), m.rmse(), 1e-9);
        assertEquals(10, m.mape().orElseThrow(), 1e-9);
    }

    @Test
    void errorNoSeLimitaArtificialmente() {
        var m = medir(List.of(new Par(100,300)));
        assertEquals(200, m.mape().orElseThrow(), 1e-9);
    }

    @Test
    void valoresRealesCeroSeExcluyenSoloDeMape() {
        var m = medir(List.of(new Par(0,10), new Par(100,80)));
        assertEquals(15, m.mae(), 1e-9);
        assertEquals(20, m.mape().orElseThrow(), 1e-9);
        assertEquals(1, m.nMape());
        assertTrue(medir(List.of(new Par(0,10))).mape().isEmpty());
    }

    @Test
    void sinObservacionesNoSeInventaPrecision() {
        assertThrows(IllegalArgumentException.class, () -> medir(List.of()));
    }

    static BigDecimal suma(List<BigDecimal> datos, int desde, int hasta) {
        return datos.subList(desde,hasta).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static List<Par> evaluar(List<BigDecimal> datos, int horizonte, boolean anterior) {
        List<Par> pares = new ArrayList<>();
        String tipo = horizonte == 7 ? "semanal" : "mensual";
        for (int corte = 30; corte + horizonte <= datos.size(); corte += horizonte) {
            // El algoritmo vigente usa el acumulado disponible, nunca datos posteriores al corte.
            var estimado = anterior ? FlujoCajaIaService.estimarMonto(suma(datos,0,corte),tipo)
                    : FlujoCajaIaService.estimarPromedioDiario(suma(datos,corte-30,corte),30,horizonte);
            var real = suma(datos,corte,corte+horizonte);
            pares.add(new Par(real.doubleValue(),estimado.doubleValue()));
        }
        return pares;
    }

    @Test
    void corteTemporalNoUsaDatosFuturos() {
        List<BigDecimal> a = new ArrayList<>();
        for (int i=0;i<37;i++) a.add(BigDecimal.TEN);
        var antes = evaluar(a,7,false).getFirst();
        a.set(36,BigDecimal.valueOf(1000));
        var despues = evaluar(a,7,false).getFirst();
        assertEquals(antes.estimado(),despues.estimado());
        assertNotEquals(antes.real(),despues.real());
        assertEquals(70,antes.estimado());
        assertEquals(70,antes.real());
    }

    @Test
    void backtestingSinteticoGeneraEvidencia() throws Exception {
        List<BigDecimal> ingresos = new ArrayList<>(), egresos = new ArrayList<>();
        for (int i=0;i<120;i++) {
            ingresos.add(BigDecimal.valueOf(100 + (i%7)*12 + i/30*8));
            egresos.add(BigDecimal.valueOf(65 + (i%7)*5 + i/30*4));
        }
        StringBuilder informe = new StringBuilder(
                "## Backtesting de exactitud predictiva\n\n" +
                "**Datos sintéticos:** 120 días; primeros 30 días de historial; cortes expansivos sin información futura. " +
                "Se comparan la regla acumulativa anterior y el promedio móvil de 30 días, no ARIMA. No es evidencia de precisión en una MYPE real.\n\n" +
                "| Modelo | Horizonte | Variable | Observaciones | MAE (PEN) | RMSE (PEN) | MAPE (%) | N MAPE |\n" +
                "|---|---|---|---:|---:|---:|---:|---:|\n");
        StringBuilder detalle = new StringBuilder("modelo,horizonte,variable,corte_dia,real,estimado\n");
        for (boolean anterior : new boolean[]{true,false}) {
        String modelo = anterior ? "Acumulativo" : "Media movil 30d";
        for (int h : new int[]{7,30}) {
            for (String variable : List.of("Ingresos","Egresos","Flujo neto")) {
                var ing = evaluar(ingresos,h,anterior);
                var egr = evaluar(egresos,h,anterior);
                List<Par> pares = new ArrayList<>();
                if (variable.equals("Ingresos")) pares.addAll(ing);
                else if (variable.equals("Egresos")) pares.addAll(egr);
                else for (int j=0;j<ing.size();j++) pares.add(new Par(
                        ing.get(j).real()-egr.get(j).real(),ing.get(j).estimado()-egr.get(j).estimado()));
                var m = medir(pares);
                assertEquals(h==7?12:3,m.n());
                assertTrue(Double.isFinite(m.mae()) && Double.isFinite(m.rmse()));
                informe.append(String.format(Locale.ROOT,"| %s | %d días | %s | %d | %.2f | %.2f | %s | %d |%n",
                        modelo,h,variable,m.n(),m.mae(),m.rmse(),
                        m.mape().isPresent()?String.format(Locale.ROOT,"%.2f",m.mape().getAsDouble()):"N/A",m.nMape()));
                for (int j=0;j<pares.size();j++) {
                    var p = pares.get(j);
                    detalle.append(String.format(Locale.ROOT,"%s,%d,%s,%d,%.2f,%.2f%n",modelo,h,variable,30+j*h,p.real(),p.estimado()));
                }
            }
        }
        }
        informe.append("\nNo se impone un umbral de aprobación predictiva sin requisito validado. " +
                "JUnit verifica la metodología; aprobar JUnit no significa que el error sea aceptable.\n");
        Path dir = Path.of("build/reports/predictive");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("summary.md"),informe);
        Files.writeString(dir.resolve("observaciones.csv"),detalle);
    }

    @Test
    void promedioMovilEscalaPorHorizonte() {
        assertEquals(new BigDecimal("700.00"), FlujoCajaIaService.estimarPromedioDiario(new BigDecimal("3000"),30,7));
        assertEquals(new BigDecimal("3000.00"), FlujoCajaIaService.estimarPromedioDiario(new BigDecimal("3000"),30,30));
    }

    @Test
    void promedioMovilSinMovimientosEsCero() {
        assertEquals(new BigDecimal("0.00"), FlujoCajaIaService.estimarPromedioDiario(BigDecimal.ZERO,30,7));
    }

    @Test
    void promedioMovilRechazaHorizonteInvalido() {
        assertThrows(IllegalArgumentException.class, () -> FlujoCajaIaService.estimarPromedioDiario(BigDecimal.TEN,0,7));
    }
}
