-- =====================================================================
-- V2 - Carga inicial de produtos do Mercado Express
-- =====================================================================
INSERT INTO TDS_Sec_MVC_TB_Mercado (nome, tipo, setor, tamanho, preco, quantidade_estoque, descricao) VALUES
('Banana Prata',            'Fruta',      'Hortifruti', '1 kg',        6.99, 80, 'Banana prata selecionada, ideal para lanches.'),
('Maçã Gala',               'Fruta',      'Hortifruti', '1 kg',        9.49, 60, 'Maçã gala nacional, doce e crocante.'),
('Tomate Italiano',         'Legume',     'Hortifruti', '1 kg',        8.90, 45, 'Tomate italiano para molhos e saladas.'),
('Alface Crespa',           'Verdura',    'Hortifruti', 'Unidade',     3.49,  4, 'Alface crespa hidropônica.'),
('Arroz Branco Tipo 1',     'Grão',       'Mercearia',  '5 kg',       27.90, 35, 'Arroz agulhinha tipo 1.'),
('Feijão Carioca',          'Grão',       'Mercearia',  '1 kg',        8.79, 50, 'Feijão carioca tipo 1.'),
('Café Torrado e Moído',    'Bebida',     'Mercearia',  '500 g',      19.90, 25, 'Café tradicional torrado e moído.'),
('Leite Integral',          'Laticínio',  'Frios',      '1 L',         5.29, 70, 'Leite UHT integral.'),
('Queijo Muçarela Fatiado', 'Laticínio',  'Frios',      '200 g',      12.99,  3, 'Muçarela fatiada para lanches.'),
('Detergente Neutro',       'Limpeza',    'Limpeza',    '500 ml',      2.79, 90, 'Detergente líquido neutro para louças.'),
('Sabão em Pó',             'Limpeza',    'Limpeza',    '1,6 kg',     21.90, 20, 'Sabão em pó para roupas brancas e coloridas.'),
('Meia Esportiva Cano Médio','Vestuário', 'Utilidades', 'Tam. 39-43', 14.90, 30, 'Par de meias esportivas de algodão.');
