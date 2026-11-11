-- Truck Booking seed data (Module 2 categories + Module 3 pricing rates).
-- Run once against a MySQL database named truck_booking, AFTER the app has
-- created the tables (spring.jpa.hibernate.ddl-auto=update), or let the
-- TruckCategorySeeder / PricingRateSeeder do the same job on startup.
-- Platform fee is 0 (decision 11 Nov 2026).

CREATE DATABASE IF NOT EXISTS truck_booking;
USE truck_booking;

INSERT IGNORE INTO truck_categories (name, tyre_count, target_payload_tons, description, active)
VALUES
  ('12 Tyre Truck', 12, 20.00, '12-tyre truck, target payload 20 tons', 1),
  ('14 Tyre Truck', 14, 25.00, '14-tyre truck, target payload 25 tons', 1),
  ('18 Tyre Truck', 18, 30.00, '18-tyre truck, target payload 30 tons', 1),
  ('22 Tyre Truck', 22, 36.00, '22-tyre truck, target payload 36 tons', 1);

-- Pricing rates: rupees per ton per km (Module 3, checked online Nov 2026).
-- Insert only after the categories above exist; match by category name.
INSERT INTO pricing_rates (category_id, rate_per_ton_km)
SELECT id, 2.70 FROM truck_categories WHERE name = '12 Tyre Truck'
  AND NOT EXISTS (SELECT 1 FROM pricing_rates WHERE category_id = truck_categories.id);
INSERT INTO pricing_rates (category_id, rate_per_ton_km)
SELECT id, 2.60 FROM truck_categories WHERE name = '14 Tyre Truck'
  AND NOT EXISTS (SELECT 1 FROM pricing_rates WHERE category_id = truck_categories.id);
INSERT INTO pricing_rates (category_id, rate_per_ton_km)
SELECT id, 2.50 FROM truck_categories WHERE name = '18 Tyre Truck'
  AND NOT EXISTS (SELECT 1 FROM pricing_rates WHERE category_id = truck_categories.id);
INSERT INTO pricing_rates (category_id, rate_per_ton_km)
SELECT id, 2.40 FROM truck_categories WHERE name = '22 Tyre Truck'
  AND NOT EXISTS (SELECT 1 FROM pricing_rates WHERE category_id = truck_categories.id);

-- Platform settings: platform fee 0, minimum charge 50% of capacity.
INSERT IGNORE INTO platform_settings (id, platform_fee, minimum_load_fraction)
VALUES (1, 0.00, 0.50);
