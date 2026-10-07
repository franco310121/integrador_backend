-- ==============================================================================
-- STOCKMASTER - CARGA DE DATOS INICIALES (SEED DATA)
-- ==============================================================================

-- A. INFORMACIÓN DE LA EMPRESA (MYPE)
INSERT INTO public.empresa (nombre_mype, ruc, direccion, telefono, correo, sector_comercial)
VALUES ('Stockmaster Soluciones MYPE S.A.C.', '20601234567', 'Av. Javier Prado Este 1234, San Isidro, Lima', '+51 987654321', 'contacto@stockmaster.pe', 'Tecnología y Retail')
ON CONFLICT (ruc) DO NOTHING;

-- B. MÉTODOS DE PAGO
INSERT INTO public.metodos_pago (nombre) VALUES
    ('Efectivo'),
    ('Tarjeta de Débito'),
    ('Tarjeta de Crédito'),
    ('Yape'),
    ('Plin'),
    ('Transferencia Bancaria')
ON CONFLICT (nombre) DO NOTHING;

-- C. CATEGORÍAS DE PRODUCTOS
INSERT INTO public.categorias (nombre, descripcion) VALUES
    ('Tecnología y Cómputo', 'Laptops, monitores, computadoras de escritorio y componentes'),
    ('Accesorios y Periféricos', 'Teclados, mouses, audífonos y cables'),
    ('Oficina y Papelería', 'Suministros de oficina, folios y artículos de escritorio')
ON CONFLICT (nombre) DO NOTHING;

-- D. PROVEEDORES
INSERT INTO public.proveedores (razon_social, ruc, telefono, correo, direccion) VALUES
    ('Distribuidora Tech Perú S.A.C.', '20551122334', '+51 1 4455667', 'ventas@techperu.com', 'Av. República de Panamá 3505, San Isidro'),
    ('Comercializadora Global S.R.L.', '20448899001', '+51 1 2233445', 'contacto@globalcorp.pe', 'Jr. Paruro 1120, Cercado de Lima')
ON CONFLICT (ruc) DO NOTHING;

-- E. PRODUCTOS
INSERT INTO public.productos (categoria_id, sku, nombre, descripcion, precio_compra, precio_venta, stock, stock_minimo, estado) VALUES
    (1, 'PROD-001', 'Laptop Lenovo ThinkPad I5 16GB', 'Intel Core i5 12va Gen, 512GB SSD NVMe', 2200.00, 2899.00, 15, 5, 'activo'),
    (1, 'PROD-002', 'Monitor LED Dell 24 Full HD', 'Panel IPS, 75Hz, HDMI/VGA', 450.00, 620.00, 10, 3, 'activo'),
    (2, 'PROD-003', 'Mouse Inalámbrico Logitech MX Master 3S', 'Sensor 8K DPI, silencioso, recargable USB-C', 280.00, 399.00, 30, 8, 'activo'),
    (2, 'PROD-004', 'Teclado Mecánico RGB Switch Red', 'Layout en español, retroiluminación RGB', 150.00, 230.00, 25, 5, 'activo'),
    (3, 'PROD-005', 'Paquete Papel Bond A4 75g (Millar)', 'Papel blanco de alta blancura para impresión', 18.00, 28.00, 50, 15, 'activo')
ON CONFLICT (sku) DO NOTHING;

-- F. CLIENTES
INSERT INTO public.clientes (nombre, tipo_documento, numero_documento, telefono, correo, direccion, estado) VALUES
    ('Juan Pérez Gómez', 'DNI', '45891234', '+51 912345678', 'juan.perez@gmail.com', 'Av. Arequipa 2300, Lince', 'activo'),
    ('Inversiones Santa Rosa E.I.R.L.', 'RUC', '20987654321', '+51 1 5554433', 'compras@santarosa.pe', 'Calle Los Pinos 450, Miraflores', 'activo')
ON CONFLICT (numero_documento) DO NOTHING;

-- G. TRANSACCIONES DE CAJA (HISTÓRICO Y SALDOS INICIALES)
INSERT INTO public.transacciones_caja (tipo, categoria_gasto, monto, fecha_transaccion, estado, observaciones) VALUES
    ('ingreso', 'Aporte de Capital', 15000.00, NOW() - INTERVAL '30 days', 'efectivizado', 'Aporte inicial de caja para operaciones MYPE'),
    ('ingreso', 'Ventas al Contado', 4500.00, NOW() - INTERVAL '20 days', 'efectivizado', 'Ingresos por venta de laptops y monitores'),
    ('egreso', 'Compra de Inventario', 6500.00, NOW() - INTERVAL '18 days', 'efectivizado', 'Pago de lote inicial a Distribuidora Tech Perú'),
    ('egreso', 'Servicios Básicos', 450.00, NOW() - INTERVAL '15 days', 'efectivizado', 'Pago de luz, agua e internet del local'),
    ('egreso', 'Alquiler de Local', 1800.00, NOW() - INTERVAL '10 days', 'efectivizado', 'Pago mensual de alquiler del establecimiento comercial'),
    ('ingreso', 'Ventas Corporativas', 3800.00, NOW() - INTERVAL '5 days', 'efectivizado', 'Venta al cliente Inversiones Santa Rosa'),
    ('ingreso', 'Ventas al Contado', 1250.00, NOW() - INTERVAL '1 days', 'efectivizado', 'Ventas minoristas de accesorios y periféricos');

-- H. MODELO PREDICTIVO IA Y PROYECCIONES DE FLUJO DE CAJA
INSERT INTO public.modelo_predictivo_ia (version_algoritmo, precision_historica)
VALUES ('ARIMA-v2.1', 94.50);

INSERT INTO public.proyecciones_flujo (modelo_id, periodo_inicio, periodo_fin, tipo_proyeccion, ingreso_proyectado, egreso_proyectado, saldo_final_estimado, nivel_confianza)
VALUES
    (1, CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', 'semanal', 5400.00, 2100.00, 19100.00, 95.00),
    (1, CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days', 'mensual', 22500.00, 9800.00, 28500.00, 92.50);

-- I. ALERTAS DE LIQUIDEZ PREVENTIVAS
INSERT INTO public.alertas_liquidez (proyeccion_id, nivel_riesgo, mensaje_alerta, recomendacion_preventiva, leido)
VALUES
    (1, 'bajo', 'Liquidez positiva proyectada para los próximos 7 días.', 'Mantener política de cobranza activa y reserva mínima de S/ 1,500.', false),
    (2, 'bajo', 'Proyección mensual con margen superavitario estimado de S/ 12,700.', 'Se sugiere evaluar inversión en reposición de stock de mayor rotación.', false);

-- J. SIMULACIONES FINANCIERAS
INSERT INTO public.simulaciones_financieras (nombre_escenario, porcentaje_ajuste_ingresos, porcentaje_ajuste_egresos, resultado_simulacion)
VALUES
    ('Escenario Optimista Q4 (+15% ventas)', 15.00, 5.00, '{"ingresosSimulados": 28117.50, "egresosSimulados": 9292.50, "saldoSimulado": 18825.00}'),
    ('Escenario Conservador (+0% ventas, +10% costos)', 0.00, 10.00, '{"ingresosSimulados": 24450.00, "egresosSimulados": 9735.00, "saldoSimulado": 14715.00}');
