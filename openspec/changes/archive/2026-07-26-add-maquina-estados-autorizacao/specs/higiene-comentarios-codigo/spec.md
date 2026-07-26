# higiene-comentarios-codigo — Delta

## ADDED Requirements

### Requirement: Javadocs e comentários de linha são concisos

Javadocs de classe e método no código Java das aplicações SHALL limitar-se ao
essencial — o quê e o porquê não óbvio, em 1–3 linhas. Procedimentos operacionais,
classificações de falha detalhadas e trade-offs SHALL viver na documentação da
aplicação (`CLAUDE.md`/`AGENTS.md`/`README.md`/`design.md`), não em blocos de
comentário no código. Comentários de linha extensos SHALL ser resumidos sob o mesmo
critério. Esta regra NÃO SHALL ser aplicada aos arquivos de documentação em si (que
permanecem detalhados) nem remover comentários que expliquem um porquê não óbvio sem
outro lar.

#### Scenario: Javadoc denso é resumido
- **WHEN** uma classe possui javadoc com mais de 3 linhas descrevendo comportamento já
  documentado nas docs da aplicação
- **THEN** o javadoc é reduzido a 1–3 linhas com o essencial, mantendo referência
  implícita ao restante nas docs

#### Scenario: Porquê não óbvio é preservado
- **WHEN** um comentário explica uma decisão não óbvia que não está registrada em
  nenhuma doc da aplicação
- **THEN** o comentário é mantido (resumido se extenso, nunca removido sem destino)

#### Scenario: Docs não são resumidas
- **WHEN** a limpeza de comentários é aplicada
- **THEN** `CLAUDE.md`, `AGENTS.md` e `README.md` não têm conteúdo resumido — apenas
  correções pontuais de exatidão (caminhos, contratos)
