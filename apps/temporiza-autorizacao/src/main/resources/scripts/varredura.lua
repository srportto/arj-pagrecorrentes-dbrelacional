-- KEYS[1] = chave da agenda (sorted set)
-- KEYS[2] = chave do stream de expiracoes
-- ARGV[1] = instante atual (epoch millis)
-- ARGV[2] = tamanho maximo do lote
--
-- Seleciona os vencidos, remove cada um do sorted set e, SOMENTE se a remocao teve
-- sucesso (ZREM retornou 1), cria a entrada no stream. O ZREM funciona como o lock:
-- em varredura concorrente de multiplas instancias, so uma consegue remover cada id,
-- entao so uma cria a entrada de trabalho correspondente. Retorna a quantidade de
-- entradas criadas.

local vencidos = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, tonumber(ARGV[2]))
local criados = 0

for _, idAutorizacao in ipairs(vencidos) do
    local removido = redis.call('ZREM', KEYS[1], idAutorizacao)
    if removido == 1 then
        redis.call('XADD', KEYS[2], '*', 'id_autorizacao', idAutorizacao)
        criados = criados + 1
    end
end

return criados
