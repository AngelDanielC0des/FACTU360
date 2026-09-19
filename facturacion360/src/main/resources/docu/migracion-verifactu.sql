-- ═══════════════════════════════════════════════════════════════════════════
-- Migracion 1 de Verifactu: el desglose impositivo.
--
-- Trae lo que necesita el desglose y NADA MAS: las dos columnas fiscales por
-- linea y la tabla donde se congela la agrupacion.
--
-- El emisor de cada factura y la hora de expedicion NO estan aqui a proposito,
-- aunque el documento los liste como requisitos previos. Ponerlos NOT NULL sin
-- el codigo Java que los rellena deja el INSERT de facturas incumpliendo el
-- esquema: MySQL responde <Field 'idemisor' doesn't have a default value> y el
-- alta de facturas deja de funcionar entera. Van en la rama de anulacion y
-- rectificacion, que es la que los usa, y en el mismo commit que su INSERT.
--
-- ─────────────────────────────────────────────────────────────────────────
-- ANTES DE EJECUTAR, COPIA DE SEGURIDAD:
--     mysqldump -u root -p bd_facturacion > copia-antes-de-verifactu.sql
--
-- No es una recomendacion de manual. Las sentencias DDL de MySQL hacen COMMIT
-- IMPLICITO: envolver esto en START TRANSACTION no sirve de nada, porque si
-- falla la quinta sentencia las cuatro anteriores YA estan aplicadas y no hay
-- ROLLBACK que las deshaga. La copia es la unica marcha atras que existe.
-- ─────────────────────────────────────────────────────────────────────────
--
-- ─────────────────────────────────────────────────────────────────────────
-- ESTE FICHERO SE EJECUTA UNA SOLA VEZ.
--
-- El CREATE TABLE lleva IF NOT EXISTS y se puede repetir, pero el ALTER no:
-- MySQL no admite ADD COLUMN IF NOT EXISTS, asi que a la segunda pasada corta
-- con <Duplicate column name 'clave_regimen'>. El recuento del principio
-- habra salido bien, asi que el error sale a mitad y parece peor de lo que
-- es: no se ha estropeado nada, es que las columnas ya estaban.
--
-- Para saber si ya la aplicaste, las tres consultas del final: si devuelven
-- una fila cada una, esta hecha y no hay que ejecutar nada.
-- ─────────────────────────────────────────────────────────────────────────
--
-- Si montas la base de datos desde cero NO necesitas este fichero:
-- backupFacturacion360v3.sql ya trae todo esto incluido.
--
-- @author AngelDanielC0des
-- ═══════════════════════════════════════════════════════════════════════════

USE `bd_facturacion`;

-- ═══ BLOQUE 0 · Comprobacion previa ════════════════════════════════════════
--
-- Cuantas filas hay en las dos tablas que se tocan. Las dos columnas nuevas
-- llevan DEFAULT, asi que estas cuentas son informativas: ninguna fila existente
-- puede quedarse a medias.

SELECT (SELECT COUNT(*) FROM `facturas`)  AS facturas_existentes,
       (SELECT COUNT(*) FROM `conceptos`) AS conceptos_existentes;


-- ═══ BLOQUE 1 · Las dos columnas fiscales por linea ════════════════════════
--
-- Sin ellas no se puede construir la clave de agrupacion del desglose: la AEAT
-- agrupa por la terna (regimen, calificacion, tipo impositivo), no solo por el
-- porcentaje. Si no existieran, todas las lineas caerian en el mismo grupo por
-- imposicion y el desglose seria falso.
--
-- Las dos llevan el valor del caso normal por DEFAULT, asi que las lineas que
-- ya hay quedan validas solas y no hace falta el patron de cuatro pasos.

ALTER TABLE `conceptos`
  ADD `clave_regimen` varchar(2) NOT NULL DEFAULT '01',   -- 01 = regimen general
  ADD `calificacion`  varchar(2) NOT NULL DEFAULT 'S1';   -- S1 = sujeta y no exenta


-- ═══ BLOQUE 2 · El desglose, congelado ═════════════════════════════════════
--
-- Guarda la agrupacion TAL Y COMO SE DECLARO. Se calcula a partir de los
-- conceptos, asi que podria recalcularse siempre... hasta el dia en que se
-- comunique a Hacienda: a partir de ahi, lo que se declaro no puede cambiar
-- aunque alguien corrija una linea. Por eso es una tabla y no una consulta.
--
-- Mismas columnas que la clave de agrupacion mas los dos importes, para que al
-- construir el XML del registro no haya que traducir nada.

CREATE TABLE IF NOT EXISTS `desglose_impositivo` (
  `iddesglose`        bigint        NOT NULL AUTO_INCREMENT,
  `idfactura`         int           NOT NULL,
  `impuesto`          varchar(2)    NOT NULL DEFAULT '01',   -- 01 = IVA
  `clave_regimen`     varchar(2)    NOT NULL DEFAULT '01',   -- 01 = regimen general
  `calificacion`      varchar(2)    NOT NULL DEFAULT 'S1',   -- S1 = sujeta y no exenta
  `tipo_impositivo`   decimal(5,2)  NOT NULL,
  `base_imponible`    decimal(12,2) NOT NULL,
  `cuota_repercutida` decimal(12,2) NOT NULL,
  PRIMARY KEY (`iddesglose`),
  KEY `ix_desglose_factura` (`idfactura`),
  CONSTRAINT `fk_desglose_factura` FOREIGN KEY (`idfactura`)
      REFERENCES `facturas` (`idfactura`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ═══ COMPROBACION FINAL ════════════════════════════════════════════════════
--
-- Las tres consultas tienen que devolver lo esperado. Si alguna falla, la
-- migracion esta a medias: restaura la copia y mira que bloque se quedo corto.

SHOW COLUMNS FROM `conceptos` LIKE 'clave\_regimen';  -- 1 fila
SHOW COLUMNS FROM `conceptos` LIKE 'calificacion';    -- 1 fila
SHOW TABLES LIKE 'desglose\_impositivo';              -- 1 fila
