## 1. arj-contratocommand — código morto e comentário stale

- [x] 1.1 `ContratocommandApplication.java`: substituir o bloco `void main()` comentado (3 linhas) por um único `// TODO: migrar para void main() (Java 25) quando o maven plugin suportar.`
- [x] 1.2 `AutorizacaoRepository.java`: corrigir o javadoc da classe — trocar "vive nas strategies e nas regras de negócio" por "vive nas rules"
- [x] 1.3 `ControleExpurgoAutorizacao.java`: remover as 2 linhas de cálculo morto (`//int particaoExpurgoMaxima = 999;` e `//int diferencaParaProximaParticao = ...`) e o comentário `//calcula diferenca...` que as precede

## 2. arj-contratocommand — comentários WHAT redundantes

- [x] 2.1 `LayoutErrosApiResponse.java`: remover o banner decorativo `//?----...`
- [x] 2.2 `BusinessException.java`: encurtar o comentário de convenção de uso (a tabela exceção→HTTP já está em `CLAUDE.md`)
- [x] 2.3 `ApplicationException.java`: encurtar o comentário de convenção de uso
- [x] 2.4 `CancelarAutorizacaoUseCase.java`: remover os 4 comentários que reafirmam a linha seguinte ("Captura partição de expurgo do momento do cancelamento", "Se a partição não mudou, apenas persistir normalmente", "Delete do banco com a chave antiga", "Altera a partição e salva novamente na nova partição") — manter o comentário que explica o `ObjectDeletedException`
- [x] 2.5 `Autorizacao.java`: remover o comentário `// Preenchimento PK e valores padrão para criação de nova autorização` e o `// não utiliza mensageria` duplicado (já explicado no comentário do campo `indicadorTipoMensageria`) — manter os comentários que decodificam indicadores/canais nos campos
- [x] 2.6 `IdContaUUIDPartitionDistributor.java`: remover o comentário `// Pega o hashCode (32 bits), garante que é positivo e tira o módulo` — manter os dois comentários de método ("ultra rápido" vs. "garantido")

## 3. arj-contratocommand — documentação do módulo

- [x] 3.1 Atualizar `CLAUDE.md` (arj-contratocommand): corrigir "usa `void main()`" para "usa `public static void main()` — `void main()` do Java 25 pendente de suporte do maven plugin (ver TODO no entrypoint)"
- [x] 3.2 Replicar a mesma correção em `AGENTS.md` (arj-contratocommand)

## 4. arj-contratoquery — código morto e comentários redundantes

- [x] 4.1 `ContratoqueryApplication.java`: substituir o bloco `void main()` comentado por um único `// TODO: migrar para void main() (Java 25) quando o maven plugin suportar.`
- [x] 4.2 `LayoutErrosApiResponse.java`: remover o banner decorativo `//?----...`
- [x] 4.3 `BusinessException.java`: encurtar o comentário de convenção de uso
- [x] 4.4 `ApplicationException.java`: encurtar o comentário de convenção de uso
- [x] 4.5 `ResourceNotFoundException.java`: encurtar o comentário de convenção de uso

## 5. arj-contratoquery — documentação do módulo

- [x] 5.1 Atualizar `CLAUDE.md` (arj-contratoquery): mesma correção do `main()` aplicada em 3.1
- [x] 5.2 Replicar a mesma correção em `AGENTS.md` (arj-contratoquery)

## 6. Verificação final

- [x] 6.1 Rodar `mvn clean compile` em `aplicacoes/arj-contratocommand` e `aplicacoes/arj-contratoquery` — sem erros
- [x] 6.2 Rodar `mvn test` nos dois módulos — suítes completas verdes, sem mudança de comportamento
- [x] 6.3 Conferir que os comentários preservados (decodificação de valores de negócio, gotchas de biblioteca, javadocs de design) continuam intactos — nenhum removido por engano
