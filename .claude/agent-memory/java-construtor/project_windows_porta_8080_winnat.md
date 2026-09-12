---
name: project_windows_porta_8080_winnat
description: Windows as vezes reserva a porta 8080 (faixa dinamica Hyper-V/WSL2), impedindo o Docker de publicar contratocommand localmente
metadata:
  type: project
---

Em 2026-09-12, `docker compose up -d contratocommand` falhou com
`Error response from daemon: ports are not available: exposing port TCP 0.0.0.0:8080 ...:
bind: An attempt was made to access a socket in a way forbidden by its access permissions`,
mesmo sem nenhum processo escutando 8080 (`netstat -ano` vazio para a porta).

**Por quê:** o Windows reserva dinamicamente faixas de porta TCP para o Hyper-V/WSL2
(`netsh interface ipv4 show excludedportrange protocol=tcp`). No momento do incidente, a faixa
`7981-8080` estava excluida, cobrindo exatamente a porta do `contratocommand`. Nao tem relacao
com o projeto nem com o `docker-compose.yml` — as demais portas (8081-8085, 8090) ficaram fora
da faixa e subiram normalmente.

**Como aplicar:** se `contratocommand` (ou qualquer serviço mapeado numa porta especifica)
falhar ao subir no Windows com essa mensagem de erro, primeiro rode o `netsh` acima para
confirmar se a porta esta na faixa excluida antes de suspeitar do compose/app. Fix que
funcionou sem reiniciar a maquina: reiniciar o serviço `winnat` num PowerShell **elevado**
(`net stop winnat` seguido de `net start winnat`) — libera a reserva imediatamente. Exige
aprovacao de UAC do usuario; nao é algo que dá para rodar sem elevação. Alternativa se o
usuario nao quiser mexer em serviço de rede do Windows: remapear a porta host do serviço no
compose (ex.: `18080:8080`) — muda documentação (README/CLAUDE.md), so fazer com confirmação
explicita, não é fix silencioso.
