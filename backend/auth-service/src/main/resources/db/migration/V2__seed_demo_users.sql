-- Parte 2: usuários de demonstração e dos agentes automatizados, com UUID fixo.
-- Os mesmos UUIDs recebem saldo e posições iniciais em
-- portfolio-service/.../V3__seed_demo_accounts_and_initial_emission.sql.
-- Senhas (somente ambiente acadêmico de demonstração; ver docs/seeds.md):
--   demo_investidor1, demo_investidor2           -> Demo@2026!
--   agent_market_maker, agent_momentum           -> Agent@2026!
-- ON CONFLICT DO NOTHING: não falha se o nome ou e-mail já estiver em uso num banco existente.

INSERT INTO users (id, username, email, password_hash) VALUES
('00000000-0000-4000-a000-000000000101', 'demo_investidor1', 'demo_investidor1@example.com',
 '$2a$10$ciczbjqsIp7RJPUIdPTftu2FBNmzewE.b0pEDqbChOy.HlLuT3a5u'),
('00000000-0000-4000-a000-000000000102', 'demo_investidor2', 'demo_investidor2@example.com',
 '$2a$10$ciczbjqsIp7RJPUIdPTftu2FBNmzewE.b0pEDqbChOy.HlLuT3a5u'),
('00000000-0000-4000-a000-000000000201', 'agent_market_maker', 'agent_market_maker@example.com',
 '$2a$10$ym7Crwoj7xaSOEpbEe2eo.ksCliaBcZ4gkZ5sx2I7uwH0T675W9va'),
('00000000-0000-4000-a000-000000000202', 'agent_momentum', 'agent_momentum@example.com',
 '$2a$10$ym7Crwoj7xaSOEpbEe2eo.ksCliaBcZ4gkZ5sx2I7uwH0T675W9va')
ON CONFLICT DO NOTHING;
