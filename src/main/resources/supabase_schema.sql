-- ==============================================================================
-- STOCKMASTER - ESQUEMA DE BASE DE DATOS SUPABASE (POSTGRESQL)
-- ==============================================================================
-- Esquema relacional completo para inventario, ventas, compras, clientes,
-- devoluciones, trazabilidad de movimientos (Kardex), auditoría de eventos,
-- transacciones de caja y módulo de IA / predicciones financieras.
-- ==============================================================================

-- 1. EXTENSIONES
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 2. TABLA: perfiles (Vinculada a auth.users de Supabase)
CREATE TABLE IF NOT EXISTS public.perfiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    nombre_completo TEXT NOT NULL,
    rol VARCHAR(20) NOT NULL DEFAULT 'vendedor' CHECK (rol IN ('admin', 'vendedor')),
    correo TEXT UNIQUE NOT NULL,
    avatar_url TEXT,
    estado VARCHAR(20) NOT NULL DEFAULT 'activo' CHECK (estado IN ('activo', 'inactivo')),
    fecha_creacion TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

COMMENT ON TABLE public.perfiles IS 'Perfiles de usuario y roles vinculados a auth.users de Supabase';

-- 3. TABLA: categorias
CREATE TABLE IF NOT EXISTS public.categorias (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(100) UNIQUE NOT NULL,
    descripcion TEXT,
    fecha_creacion TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 4. TABLA: proveedores
CREATE TABLE IF NOT EXISTS public.proveedores (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    razon_social VARCHAR(150) NOT NULL,
    ruc VARCHAR(20) UNIQUE,
    telefono VARCHAR(255),
    correo VARCHAR(150),
    direccion VARCHAR(500),
    fecha_creacion TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 5. TABLA: metodos_pago
CREATE TABLE IF NOT EXISTS public.metodos_pago (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(50) UNIQUE NOT NULL
);

-- 6. TABLA: productos
CREATE TABLE IF NOT EXISTS public.productos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    categoria_id BIGINT NOT NULL REFERENCES public.categorias(id) ON DELETE RESTRICT,
    sku VARCHAR(50) UNIQUE,
    nombre VARCHAR(150) NOT NULL,
    descripcion TEXT,
    imagen_url TEXT,
    precio_compra NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (precio_compra >= 0),
    precio_venta NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (precio_venta >= 0),
    stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    stock_minimo INTEGER NOT NULL DEFAULT 5 CHECK (stock_minimo >= 0),
    estado VARCHAR(20) NOT NULL DEFAULT 'activo' CHECK (estado IN ('activo', 'inactivo')),
    fecha_creacion TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    fecha_actualizacion TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 7. TABLA: clientes
CREATE TABLE IF NOT EXISTS public.clientes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    tipo_documento VARCHAR(20) NOT NULL DEFAULT 'DNI' CHECK (tipo_documento IN ('DNI', 'RUC')),
    numero_documento VARCHAR(20) UNIQUE NOT NULL,
    telefono VARCHAR(255),
    correo VARCHAR(150),
    direccion VARCHAR(500),
    estado VARCHAR(20) NOT NULL DEFAULT 'activo' CHECK (estado IN ('activo', 'inactivo')),
    fecha_creacion TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 8. TABLA: ventas
CREATE TABLE IF NOT EXISTS public.ventas (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero_boleta VARCHAR(50) UNIQUE NOT NULL,
    vendedor_id UUID NOT NULL REFERENCES public.perfiles(id) ON DELETE RESTRICT,
    cliente VARCHAR(150) NOT NULL,
    cliente_id BIGINT REFERENCES public.clientes(id) ON DELETE SET NULL,
    metodo_pago_id BIGINT REFERENCES public.metodos_pago(id) ON DELETE SET NULL,
    subtotal NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0),
    igv NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (igv >= 0),
    descuento NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (descuento >= 0),
    total NUMERIC(12, 2) NOT NULL CHECK (total >= 0),
    estado VARCHAR(20) NOT NULL DEFAULT 'completada' CHECK (estado IN ('completada', 'anulada')),
    observaciones TEXT,
    fecha_venta TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 9. TABLA: detalle_ventas
CREATE TABLE IF NOT EXISTS public.detalle_ventas (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES public.ventas(id) ON DELETE CASCADE,
    producto_id BIGINT NOT NULL REFERENCES public.productos(id) ON DELETE RESTRICT,
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    precio_unitario NUMERIC(12, 2) NOT NULL CHECK (precio_unitario >= 0),
    subtotal NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0)
);

-- 10. TABLA: compras
CREATE TABLE IF NOT EXISTS public.compras (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    proveedor_id BIGINT NOT NULL REFERENCES public.proveedores(id) ON DELETE RESTRICT,
    usuario_id UUID NOT NULL REFERENCES public.perfiles(id) ON DELETE RESTRICT,
    subtotal NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0),
    igv NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (igv >= 0),
    total NUMERIC(12, 2) NOT NULL CHECK (total >= 0),
    fecha_compra TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 11. TABLA: detalle_compras
CREATE TABLE IF NOT EXISTS public.detalle_compras (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    compra_id BIGINT NOT NULL REFERENCES public.compras(id) ON DELETE CASCADE,
    producto_id BIGINT NOT NULL REFERENCES public.productos(id) ON DELETE RESTRICT,
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    costo_unitario NUMERIC(12, 2) NOT NULL CHECK (costo_unitario >= 0),
    subtotal NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0)
);

-- 12. TABLA: movimientos_stock
CREATE TABLE IF NOT EXISTS public.movimientos_stock (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    producto_id BIGINT NOT NULL REFERENCES public.productos(id) ON DELETE RESTRICT,
    usuario_id UUID NOT NULL REFERENCES public.perfiles(id) ON DELETE RESTRICT,
    tipo VARCHAR(50) NOT NULL,
    cantidad INTEGER NOT NULL CHECK (cantidad != 0),
    motivo TEXT,
    fecha_movimiento TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 13. TABLA: devoluciones
CREATE TABLE IF NOT EXISTS public.devoluciones (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero_devolucion VARCHAR(50) UNIQUE NOT NULL,
    venta_id BIGINT NOT NULL REFERENCES public.ventas(id) ON DELETE RESTRICT,
    usuario_id UUID NOT NULL REFERENCES public.perfiles(id) ON DELETE RESTRICT,
    cliente VARCHAR(150) NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    destino_stock VARCHAR(50) NOT NULL DEFAULT 'reingreso' CHECK (destino_stock IN ('reingreso', 'merma')),
    metodo_reembolso VARCHAR(50) NOT NULL DEFAULT 'efectivo',
    monto_total NUMERIC(12, 2) NOT NULL CHECK (monto_total >= 0),
    estado VARCHAR(20) NOT NULL DEFAULT 'completada' CHECK (estado IN ('completada', 'anulada')),
    observaciones TEXT,
    fecha_devolucion TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 14. TABLA: detalle_devoluciones
CREATE TABLE IF NOT EXISTS public.detalle_devoluciones (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    devolucion_id BIGINT NOT NULL REFERENCES public.devoluciones(id) ON DELETE CASCADE,
    producto_id BIGINT NOT NULL REFERENCES public.productos(id) ON DELETE RESTRICT,
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    precio_unitario NUMERIC(12, 2) NOT NULL CHECK (precio_unitario >= 0),
    subtotal NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0)
);

-- 15. TABLA: auditoria_logs
CREATE TABLE IF NOT EXISTS public.auditoria_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_recurso VARCHAR(200) NOT NULL,
    accion VARCHAR(50) NOT NULL CHECK (accion IN ('CREAR', 'EDITAR', 'VER', 'ELIMINAR')),
    quien_lo_hizo VARCHAR(150) NOT NULL,
    usuario_correo VARCHAR(150),
    modulo VARCHAR(50) NOT NULL,
    detalle TEXT,
    fecha TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 16. TABLA: transacciones_caja
CREATE TABLE IF NOT EXISTS public.transacciones_caja (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES public.perfiles(id) ON DELETE RESTRICT,
    venta_id BIGINT REFERENCES public.ventas(id) ON DELETE SET NULL,
    compra_id BIGINT REFERENCES public.compras(id) ON DELETE SET NULL,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('ingreso', 'egreso')),
    categoria_gasto VARCHAR(100),
    monto NUMERIC(12, 2) NOT NULL CHECK (monto > 0),
    fecha_transaccion TIMESTAMPTZ NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'efectivizado' CHECK (estado IN ('efectivizado', 'pendiente', 'anulado')),
    observaciones TEXT,
    fecha_creacion TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 17. TABLA: modelo_predictivo_ia
CREATE TABLE IF NOT EXISTS public.modelo_predictivo_ia (
    id_modelo BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version_algoritmo VARCHAR(50) NOT NULL,
    precision_historica NUMERIC(5, 2) CHECK (precision_historica >= 0 AND precision_historica <= 100),
    fecha_entrenamiento TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 18. TABLA: proyecciones_flujo
CREATE TABLE IF NOT EXISTS public.proyecciones_flujo (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    modelo_id BIGINT REFERENCES public.modelo_predictivo_ia(id_modelo) ON DELETE SET NULL,
    periodo_inicio DATE NOT NULL,
    periodo_fin DATE NOT NULL,
    tipo_proyeccion VARCHAR(20) NOT NULL CHECK (tipo_proyeccion IN ('semanal', 'mensual')),
    ingreso_proyectado NUMERIC(12, 2) NOT NULL CHECK (ingreso_proyectado >= 0),
    egreso_proyectado NUMERIC(12, 2) NOT NULL CHECK (egreso_proyectado >= 0),
    saldo_final_estimado NUMERIC(12, 2) NOT NULL,
    nivel_confianza NUMERIC(5, 2) CHECK (nivel_confianza >= 0 AND nivel_confianza <= 100),
    fecha_generacion TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 19. TABLA: alertas_liquidez
CREATE TABLE IF NOT EXISTS public.alertas_liquidez (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    proyeccion_id BIGINT REFERENCES public.proyecciones_flujo(id) ON DELETE CASCADE,
    nivel_riesgo VARCHAR(20) NOT NULL CHECK (nivel_riesgo IN ('bajo', 'medio', 'critico')),
    mensaje_alerta TEXT NOT NULL,
    recomendacion_preventiva TEXT,
    fecha_alerta TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    leido BOOLEAN DEFAULT FALSE
);

-- 20. TABLA: simulaciones_financieras
CREATE TABLE IF NOT EXISTS public.simulaciones_financieras (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id UUID REFERENCES public.perfiles(id) ON DELETE SET NULL,
    nombre_escenario VARCHAR(150) NOT NULL,
    porcentaje_ajuste_ingresos NUMERIC(5, 2) DEFAULT 0.00,
    porcentaje_ajuste_egresos NUMERIC(5, 2) DEFAULT 0.00,
    resultado_simulacion JSONB,
    fecha_simulacion TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 21. TABLA: empresa (Información de la MYPE)
CREATE TABLE IF NOT EXISTS public.empresa (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_mype VARCHAR(150) NOT NULL,
    ruc VARCHAR(20) UNIQUE NOT NULL,
    direccion VARCHAR(255),
    telefono VARCHAR(50),
    correo VARCHAR(150),
    sector_comercial VARCHAR(100),
    fecha_creacion TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- ==============================================================================
-- 21. TRIGGERS Y FUNCIONES AUTOMÁTICAS
-- ==============================================================================

-- A. Actualizar automáticamente fecha_actualizacion en productos
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.fecha_actualizacion = timezone('utc'::text, now());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER tr_productos_updated_at
BEFORE UPDATE ON public.productos
FOR EACH ROW
EXECUTE FUNCTION public.handle_updated_at();

-- B. Sincronizar nuevo usuario desde auth.users a public.perfiles
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.perfiles (id, nombre_completo, rol, correo, estado)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'nombre_completo', split_part(NEW.email, '@', 1)),
        COALESCE(NEW.raw_user_meta_data->>'rol', 'vendedor'),
        NEW.email,
        'activo'
    )
    ON CONFLICT (id) DO UPDATE
    SET correo = EXCLUDED.correo;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ==============================================================================
-- 22. ROW LEVEL SECURITY (RLS)
-- ==============================================================================
ALTER TABLE public.perfiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categorias ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.proveedores ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.productos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.metodos_pago ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.clientes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ventas ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.detalle_ventas ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.compras ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.detalle_compras ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.movimientos_stock ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devoluciones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.detalle_devoluciones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.auditoria_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transacciones_caja ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.modelo_predictivo_ia ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.proyecciones_flujo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.alertas_liquidez ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.simulaciones_financieras ENABLE ROW LEVEL SECURITY;

-- Función helper para verificar rol admin
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.perfiles
        WHERE id = auth.uid() AND rol = 'admin' AND estado = 'activo'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DO $$
BEGIN
    -- Lectura pública para usuarios autenticados
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura perfiles' AND tablename = 'perfiles') THEN
        CREATE POLICY "Permitir lectura perfiles" ON public.perfiles FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura categorias' AND tablename = 'categorias') THEN
        CREATE POLICY "Permitir lectura categorias" ON public.categorias FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura proveedores' AND tablename = 'proveedores') THEN
        CREATE POLICY "Permitir lectura proveedores" ON public.proveedores FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura productos' AND tablename = 'productos') THEN
        CREATE POLICY "Permitir lectura productos" ON public.productos FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura metodos_pago' AND tablename = 'metodos_pago') THEN
        CREATE POLICY "Permitir lectura metodos_pago" ON public.metodos_pago FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura clientes' AND tablename = 'clientes') THEN
        CREATE POLICY "Permitir lectura clientes" ON public.clientes FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura ventas' AND tablename = 'ventas') THEN
        CREATE POLICY "Permitir lectura ventas" ON public.ventas FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura detalle_ventas' AND tablename = 'detalle_ventas') THEN
        CREATE POLICY "Permitir lectura detalle_ventas" ON public.detalle_ventas FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura compras' AND tablename = 'compras') THEN
        CREATE POLICY "Permitir lectura compras" ON public.compras FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura detalle_compras' AND tablename = 'detalle_compras') THEN
        CREATE POLICY "Permitir lectura detalle_compras" ON public.detalle_compras FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura movimientos_stock' AND tablename = 'movimientos_stock') THEN
        CREATE POLICY "Permitir lectura movimientos_stock" ON public.movimientos_stock FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura devoluciones' AND tablename = 'devoluciones') THEN
        CREATE POLICY "Permitir lectura devoluciones" ON public.devoluciones FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura detalle_devoluciones' AND tablename = 'detalle_devoluciones') THEN
        CREATE POLICY "Permitir lectura detalle_devoluciones" ON public.detalle_devoluciones FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura auditoria_logs' AND tablename = 'auditoria_logs') THEN
        CREATE POLICY "Permitir lectura auditoria_logs" ON public.auditoria_logs FOR SELECT TO authenticated USING (public.is_admin());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura transacciones_caja' AND tablename = 'transacciones_caja') THEN
        CREATE POLICY "Permitir lectura transacciones_caja" ON public.transacciones_caja FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura modelo_predictivo_ia' AND tablename = 'modelo_predictivo_ia') THEN
        CREATE POLICY "Permitir lectura modelo_predictivo_ia" ON public.modelo_predictivo_ia FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura proyecciones_flujo' AND tablename = 'proyecciones_flujo') THEN
        CREATE POLICY "Permitir lectura proyecciones_flujo" ON public.proyecciones_flujo FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura alertas_liquidez' AND tablename = 'alertas_liquidez') THEN
        CREATE POLICY "Permitir lectura alertas_liquidez" ON public.alertas_liquidez FOR SELECT TO authenticated USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir lectura simulaciones_financieras' AND tablename = 'simulaciones_financieras') THEN
        CREATE POLICY "Permitir lectura simulaciones_financieras" ON public.simulaciones_financieras FOR SELECT TO authenticated USING (true);
    END IF;

    -- Gestión de clientes por usuarios autenticados
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir gestionar clientes' AND tablename = 'clientes') THEN
        CREATE POLICY "Permitir gestionar clientes" ON public.clientes FOR ALL TO authenticated USING (true) WITH CHECK (true);
    END IF;

    -- Ventas y devoluciones por usuarios autenticados
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir crear ventas' AND tablename = 'ventas') THEN
        CREATE POLICY "Permitir crear ventas" ON public.ventas FOR INSERT TO authenticated WITH CHECK (auth.uid() = vendedor_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir crear detalle_ventas' AND tablename = 'detalle_ventas') THEN
        CREATE POLICY "Permitir crear detalle_ventas" ON public.detalle_ventas FOR INSERT TO authenticated WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir anular ventas' AND tablename = 'ventas') THEN
        CREATE POLICY "Permitir anular ventas" ON public.ventas FOR UPDATE TO authenticated USING (public.is_admin() OR auth.uid() = vendedor_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir crear devoluciones' AND tablename = 'devoluciones') THEN
        CREATE POLICY "Permitir crear devoluciones" ON public.devoluciones FOR INSERT TO authenticated WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir crear detalle_devoluciones' AND tablename = 'detalle_devoluciones') THEN
        CREATE POLICY "Permitir crear detalle_devoluciones" ON public.detalle_devoluciones FOR INSERT TO authenticated WITH CHECK (true);
    END IF;

    -- Políticas administrativas y de caja / IA
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Admin gestiona categorias' AND tablename = 'categorias') THEN
        CREATE POLICY "Admin gestiona categorias" ON public.categorias FOR ALL TO authenticated USING (public.is_admin());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Admin gestiona proveedores' AND tablename = 'proveedores') THEN
        CREATE POLICY "Admin gestiona proveedores" ON public.proveedores FOR ALL TO authenticated USING (public.is_admin());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Admin gestiona productos' AND tablename = 'productos') THEN
        CREATE POLICY "Admin gestiona productos" ON public.productos FOR ALL TO authenticated USING (public.is_admin());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Admin gestiona compras' AND tablename = 'compras') THEN
        CREATE POLICY "Admin gestiona compras" ON public.compras FOR ALL TO authenticated USING (public.is_admin());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Admin gestiona detalle_compras' AND tablename = 'detalle_compras') THEN
        CREATE POLICY "Admin gestiona detalle_compras" ON public.detalle_compras FOR ALL TO authenticated USING (public.is_admin());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Admin gestiona perfiles' AND tablename = 'perfiles') THEN
        CREATE POLICY "Admin gestiona perfiles" ON public.perfiles FOR ALL TO authenticated USING (public.is_admin());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir insertar movimientos stock' AND tablename = 'movimientos_stock') THEN
        CREATE POLICY "Permitir insertar movimientos stock" ON public.movimientos_stock FOR INSERT TO authenticated WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir registrar auditoria' AND tablename = 'auditoria_logs') THEN
        CREATE POLICY "Permitir registrar auditoria" ON public.auditoria_logs FOR INSERT TO authenticated WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir gestionar transacciones caja' AND tablename = 'transacciones_caja') THEN
        CREATE POLICY "Permitir gestionar transacciones caja" ON public.transacciones_caja FOR ALL TO authenticated USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Permitir gestionar simulaciones financieras' AND tablename = 'simulaciones_financieras') THEN
        CREATE POLICY "Permitir gestionar simulaciones financieras" ON public.simulaciones_financieras FOR ALL TO authenticated USING (true) WITH CHECK (true);
    END IF;
END $$;

-- ==============================================================================
-- 23. CONFIGURACIÓN BÁSICA DE MÉTODOS DE PAGO
-- ==============================================================================
INSERT INTO public.metodos_pago (nombre) VALUES
    ('Efectivo'),
    ('Tarjeta de Débito'),
    ('Tarjeta de Crédito'),
    ('Yape'),
    ('Plin'),
    ('Transferencia Bancaria')
ON CONFLICT (nombre) DO NOTHING;

-- ==============================================================================
-- 24. CARGA DE DATOS INICIALES (SEED DATA)
-- ==============================================================================

-- A. INFORMACIÓN DE LA EMPRESA (MYPE)
INSERT INTO public.empresa (nombre_mype, ruc, direccion, telefono, correo, sector_comercial)
VALUES ('Stockmaster Soluciones MYPE S.A.C.', '20601234567', 'Av. Javier Prado Este 1234, San Isidro, Lima', '+51 987654321', 'contacto@stockmaster.pe', 'Tecnología y Retail')
ON CONFLICT (ruc) DO NOTHING;

-- B. CATEGORÍAS DE PRODUCTOS
INSERT INTO public.categorias (nombre, descripcion) VALUES
    ('Tecnología y Cómputo', 'Laptops, monitores, computadoras de escritorio y componentes'),
    ('Accesorios y Periféricos', 'Teclados, mouses, audífonos y cables'),
    ('Oficina y Papelería', 'Suministros de oficina, folios y artículos de escritorio')
ON CONFLICT (nombre) DO NOTHING;

-- C. PROVEEDORES
INSERT INTO public.proveedores (razon_social, ruc, telefono, correo, direccion) VALUES
    ('Distribuidora Tech Perú S.A.C.', '20551122334', '+51 1 4455667', 'ventas@techperu.com', 'Av. República de Panamá 3505, San Isidro'),
    ('Comercializadora Global S.R.L.', '20448899001', '+51 1 2233445', 'contacto@globalcorp.pe', 'Jr. Paruro 1120, Cercado de Lima')
ON CONFLICT (ruc) DO NOTHING;

-- D. PRODUCTOS
INSERT INTO public.productos (categoria_id, sku, nombre, descripcion, precio_compra, precio_venta, stock, stock_minimo, estado) VALUES
    (1, 'PROD-001', 'Laptop Lenovo ThinkPad I5 16GB', 'Intel Core i5 12va Gen, 512GB SSD NVMe', 2200.00, 2899.00, 15, 5, 'activo'),
    (1, 'PROD-002', 'Monitor LED Dell 24 Full HD', 'Panel IPS, 75Hz, HDMI/VGA', 450.00, 620.00, 10, 3, 'activo'),
    (2, 'PROD-003', 'Mouse Inalámbrico Logitech MX Master 3S', 'Sensor 8K DPI, silencioso, recargable USB-C', 280.00, 399.00, 30, 8, 'activo'),
    (2, 'PROD-004', 'Teclado Mecánico RGB Switch Red', 'Layout en español, retroiluminación RGB', 150.00, 230.00, 25, 5, 'activo'),
    (3, 'PROD-005', 'Paquete Papel Bond A4 75g (Millar)', 'Papel blanco de alta blancura para impresión', 18.00, 28.00, 50, 15, 'activo')
ON CONFLICT (sku) DO NOTHING;

-- E. CLIENTES
INSERT INTO public.clientes (nombre, tipo_documento, numero_documento, telefono, correo, direccion, estado) VALUES
    ('Juan Pérez Gómez', 'DNI', '45891234', '+51 912345678', 'juan.perez@gmail.com', 'Av. Arequipa 2300, Lince', 'activo'),
    ('Inversiones Santa Rosa E.I.R.L.', 'RUC', '20987654321', '+51 1 5554433', 'compras@santarosa.pe', 'Calle Los Pinos 450, Miraflores', 'activo')
ON CONFLICT (numero_documento) DO NOTHING;

-- F. TRANSACCIONES DE CAJA (HISTÓRICO Y SALDOS INICIALES)
INSERT INTO public.transacciones_caja (tipo, categoria_gasto, monto, fecha_transaccion, estado, observaciones) VALUES
    ('ingreso', 'Aporte de Capital', 15000.00, NOW() - INTERVAL '30 days', 'efectivizado', 'Aporte inicial de caja para operaciones MYPE'),
    ('ingreso', 'Ventas al Contado', 4500.00, NOW() - INTERVAL '20 days', 'efectivizado', 'Ingresos por venta de laptops y monitores'),
    ('egreso', 'Compra de Inventario', 6500.00, NOW() - INTERVAL '18 days', 'efectivizado', 'Pago de lote inicial a Distribuidora Tech Perú'),
    ('egreso', 'Servicios Básicos', 450.00, NOW() - INTERVAL '15 days', 'efectivizado', 'Pago de luz, agua e internet del local'),
    ('egreso', 'Alquiler de Local', 1800.00, NOW() - INTERVAL '10 days', 'efectivizado', 'Pago mensual de alquiler del establecimiento comercial'),
    ('ingreso', 'Ventas Corporativas', 3800.00, NOW() - INTERVAL '5 days', 'efectivizado', 'Venta al cliente Inversiones Santa Rosa'),
    ('ingreso', 'Ventas al Contado', 1250.00, NOW() - INTERVAL '1 days', 'efectivizado', 'Ventas minoristas de accesorios y periféricos');

-- G. MODELO PREDICTIVO IA Y PROYECCIONES DE FLUJO DE CAJA
INSERT INTO public.modelo_predictivo_ia (version_algoritmo, precision_historica)
VALUES ('ARIMA-v2.1', 94.50);

INSERT INTO public.proyecciones_flujo (modelo_id, periodo_inicio, periodo_fin, tipo_proyeccion, ingreso_proyectado, egreso_proyectado, saldo_final_estimado, nivel_confianza)
VALUES
    (1, CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', 'semanal', 5400.00, 2100.00, 19100.00, 95.00),
    (1, CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days', 'mensual', 22500.00, 9800.00, 28500.00, 92.50);

-- H. ALERTAS DE LIQUIDEZ PREVENTIVAS
INSERT INTO public.alertas_liquidez (proyeccion_id, nivel_riesgo, mensaje_alerta, recomendacion_preventiva, leido)
VALUES
    (1, 'bajo', 'Liquidez positiva proyectada para los próximos 7 días.', 'Mantener política de cobranza activa y reserva mínima de S/ 1,500.', false),
    (2, 'bajo', 'Proyección mensual con margen superavitario estimado de S/ 12,700.', 'Se sugiere evaluar inversión en reposición de stock de mayor rotación.', false);

-- I. SIMULACIONES FINANCIERAS
INSERT INTO public.simulaciones_financieras (nombre_escenario, porcentaje_ajuste_ingresos, porcentaje_ajuste_egresos, resultado_simulacion)
VALUES
    ('Escenario Optimista Q4 (+15% ventas)', 15.00, 5.00, '{"ingresosSimulados": 28117.50, "egresosSimulados": 9292.50, "saldoSimulado": 18825.00}'),
    ('Escenario Conservador (+0% ventas, +10% costos)', 0.00, 10.00, '{"ingresosSimulados": 24450.00, "egresosSimulados": 9735.00, "saldoSimulado": 14715.00}');

