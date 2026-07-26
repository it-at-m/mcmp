SET client_encoding = 'UTF8';

-- Fügt das Feld login_page zur Tabelle cmp.user hinzu
ALTER TABLE cmp."user"
ADD COLUMN login_page VARCHAR(255);

-- Optional: Standardwert für bestehende Benutzer setzen
UPDATE cmp."user"
SET login_page = '/appservice'
WHERE login_page IS NULL;