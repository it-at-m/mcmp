SET client_encoding = 'UTF8';

CREATE OR REPLACE FUNCTION format_nested_json(input_text TEXT)
    RETURNS TEXT AS $$
DECLARE
    parsed_json JSONB;
    result JSONB;
    key TEXT;
    value TEXT;
BEGIN
    parsed_json := input_text::JSONB;
    result := '{}'::JSONB;
    FOR key, value IN SELECT * FROM jsonb_each_text(parsed_json)
        LOOP
            BEGIN
                result := result || jsonb_build_object(key, value::JSONB);
            EXCEPTION WHEN OTHERS THEN
                result := result || jsonb_build_object(key, value);
            END;
        END LOOP;

    RETURN jsonb_pretty(result);
END;
$$ LANGUAGE plpgsql;

UPDATE cmp.job
SET awx_variables = format_nested_json(awx_variables)
WHERE awx_variables IS NOT NULL
  AND awx_variables != '';

DROP FUNCTION format_nested_json(TEXT);
