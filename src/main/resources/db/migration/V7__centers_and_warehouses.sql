-- V7: Centers and Warehouses Schema
-- This migration adds the Center-Warehouse-Location hierarchy
-- and Purchase Order system tables

-- ============================================
-- 1. CENTERS TABLE
-- ============================================
CREATE TABLE centers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    address TEXT,
    phone VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE centers IS '동일 지역의 창고 그룹. 센터 단위 통합 재고 조회 가능';
COMMENT ON COLUMN centers.code IS '센터 고유 코드 (글로벌 유니크)';
COMMENT ON COLUMN centers.status IS 'ACTIVE, INACTIVE, CLOSED';

-- ============================================
-- 2. WAREHOUSES TABLE
-- ============================================
CREATE TABLE warehouses (
    id BIGSERIAL PRIMARY KEY,
    center_id BIGINT NOT NULL REFERENCES centers(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    address TEXT,
    phone VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(center_id, code)
);

COMMENT ON TABLE warehouses IS '물리적 건축물. 센터에 소속되며 실제 입출고 발생 장소';
COMMENT ON COLUMN warehouses.code IS '창고 코드 (센터 내 유니크)';
COMMENT ON COLUMN warehouses.status IS 'ACTIVE, INACTIVE, CLOSED';

CREATE INDEX idx_warehouses_center_id ON warehouses(center_id);
CREATE INDEX idx_warehouses_status ON warehouses(status);

-- ============================================
-- 3. UPDATE LOCATIONS TABLE (Add warehouse_id)
-- ============================================
-- 기존 locations 테이블에 warehouse_id 컬럼 추가
ALTER TABLE locations 
    ADD COLUMN warehouse_id BIGINT REFERENCES warehouses(id);

COMMENT ON COLUMN locations.warehouse_id IS '소속 창고 ID';

CREATE INDEX idx_locations_warehouse_id ON locations(warehouse_id);

-- ============================================
-- 4. PURCHASE ORDERS TABLE
-- ============================================
CREATE TABLE purchase_orders (
    id BIGSERIAL PRIMARY KEY,
    po_number VARCHAR(100) NOT NULL UNIQUE,
    requesting_center_id BIGINT NOT NULL REFERENCES centers(id),
    target_warehouse_id BIGINT REFERENCES warehouses(id),
    supplier_name VARCHAR(255),
    supplier_code VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    erp_reference VARCHAR(255),
    requested_by BIGINT REFERENCES users(id),
    requested_at TIMESTAMP WITH TIME ZONE,
    erp_responded_at TIMESTAMP WITH TIME ZONE,
    cancel_reason TEXT,
    total_requested_amount DECIMAL(15, 2) DEFAULT 0,
    total_accepted_amount DECIMAL(15, 2) DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE purchase_orders IS '발주 헤더. 센터에서 요청하고 창고로 입고';
COMMENT ON COLUMN purchase_orders.status IS 'DRAFT, REQUESTED, ACCEPTED, PARTIALLY_ACCEPTED, REJECTED, CANCELLED, SHIPMENT_CREATED, IN_TRANSIT, COMPLETED';

CREATE INDEX idx_purchase_orders_center_id ON purchase_orders(requesting_center_id);
CREATE INDEX idx_purchase_orders_warehouse_id ON purchase_orders(target_warehouse_id);
CREATE INDEX idx_purchase_orders_status ON purchase_orders(status);
CREATE INDEX idx_purchase_orders_po_number ON purchase_orders(po_number);

-- ============================================
-- 5. PURCHASE ORDER ITEMS TABLE
-- ============================================
CREATE TABLE purchase_order_items (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    requested_quantity INTEGER NOT NULL CHECK (requested_quantity > 0),
    accepted_quantity INTEGER DEFAULT 0 CHECK (accepted_quantity >= 0),
    cancelled_quantity INTEGER DEFAULT 0 CHECK (cancelled_quantity >= 0),
    unit_price DECIMAL(12, 2),
    total_price DECIMAL(15, 2),
    note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE purchase_order_items IS '발주 품목 상세';

CREATE INDEX idx_purchase_order_items_po_id ON purchase_order_items(purchase_order_id);
CREATE INDEX idx_purchase_order_items_product_id ON purchase_order_items(product_id);

-- ============================================
-- 6. PURCHASE ORDER SHIPMENTS TABLE
-- ============================================
CREATE TABLE purchase_order_shipments (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id),
    shipment_number VARCHAR(100) NOT NULL,
    carrier VARCHAR(100),
    tracking_number VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    shipped_at TIMESTAMP WITH TIME ZONE,
    eta_date DATE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE purchase_order_shipments IS '발송 정보. 부분 발송 가능';

CREATE INDEX idx_purchase_order_shipments_po_id ON purchase_order_shipments(purchase_order_id);
CREATE INDEX idx_purchase_order_shipments_status ON purchase_order_shipments(status);

-- ============================================
-- 7. PURCHASE ORDER SHIPMENT ITEMS TABLE
-- ============================================
CREATE TABLE purchase_order_shipment_items (
    id BIGSERIAL PRIMARY KEY,
    shipment_id BIGINT NOT NULL REFERENCES purchase_order_shipments(id) ON DELETE CASCADE,
    purchase_order_item_id BIGINT NOT NULL REFERENCES purchase_order_items(id),
    shipped_quantity INTEGER NOT NULL CHECK (shipped_quantity > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE purchase_order_shipment_items IS '발송 품목 상세';

CREATE INDEX idx_purchase_order_shipment_items_shipment_id ON purchase_order_shipment_items(shipment_id);
CREATE INDEX idx_purchase_order_shipment_items_item_id ON purchase_order_shipment_items(purchase_order_item_id);

-- ============================================
-- 8. UPDATE INBOUNDS TABLE (Add purchase_order_shipment_id)
-- ============================================
ALTER TABLE inbounds 
    ADD COLUMN purchase_order_shipment_id BIGINT REFERENCES purchase_order_shipments(id);

COMMENT ON COLUMN inbounds.purchase_order_shipment_id IS '연결된 발송 ID (발주 기반 입고)';

CREATE INDEX idx_inbounds_po_shipment_id ON inbounds(purchase_order_shipment_id);
