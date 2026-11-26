-- Parte 2: emissão inicial dos ativos e contas de demonstração/agentes.
-- Sem venda a descoberto, só é possível negociar se alguém começar com ações.
-- Os UUIDs são os mesmos de auth-service/.../V2__seed_demo_users.sql. Detalhes em docs/seeds.md.
--
-- Emissão por ativo (igual para os 5 ativos):
--   agent_market_maker  5 000  (provedor de liquidez)
--   agent_momentum        500
--   demo_investidor1      200
--   demo_investidor2      200
--   total               5 900 ações por ativo
-- Preço médio inicial = preço inicial do ativo (market V1). Os valores são repetidos aqui porque,
-- num banco novo, o schema market ainda não existe quando esta migration roda.

INSERT INTO accounts (id, user_id, balance, reserved_balance, version) VALUES
(gen_random_uuid(), '00000000-0000-4000-a000-000000000101',  100000.0000, 0, 0),
(gen_random_uuid(), '00000000-0000-4000-a000-000000000102',  100000.0000, 0, 0),
(gen_random_uuid(), '00000000-0000-4000-a000-000000000201', 1000000.0000, 0, 0),
(gen_random_uuid(), '00000000-0000-4000-a000-000000000202',  200000.0000, 0, 0)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO portfolio_positions (id, user_id, asset_id, quantity, reserved_quantity, average_price, version)
SELECT gen_random_uuid(), holder.user_id, asset.asset_id, holder.quantity, 0, asset.initial_price, 0
FROM (VALUES
        ('00000000-0000-4000-a000-000000000101'::uuid,  200::bigint),
        ('00000000-0000-4000-a000-000000000102'::uuid,  200::bigint),
        ('00000000-0000-4000-a000-000000000201'::uuid, 5000::bigint),
        ('00000000-0000-4000-a000-000000000202'::uuid,  500::bigint)
     ) AS holder(user_id, quantity)
CROSS JOIN (VALUES
        ('11111111-1111-1111-1111-111111111111'::uuid, 25.0000::numeric),  -- TECH3
        ('22222222-2222-2222-2222-222222222222'::uuid, 18.0000::numeric),  -- BANK4
        ('33333333-3333-3333-3333-333333333333'::uuid, 32.0000::numeric),  -- ENER3
        ('44444444-4444-4444-4444-444444444444'::uuid, 14.5000::numeric),  -- FOOD3
        ('55555555-5555-5555-5555-555555555555'::uuid, 21.0000::numeric)   -- RETL3
     ) AS asset(asset_id, initial_price)
ON CONFLICT (user_id, asset_id) DO NOTHING;
