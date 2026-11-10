# Registro de ciclos reais de TDD

Os logs em `evidencias-tdd/` foram gravados durante a execução, com os testes
introduzidos antes de cada comportamento. Cada arquivo `red` registra a falha
anterior à implementação; o `green` correspondente registra a suíte passando.

Antes dos ciclos, foi necessário instalar o JDK 21: havia apenas o runtime, sem
`javac`. Falhas de ambiente não foram contadas como testes vermelhos.

## Ciclo 1 — entradas inválidas

- Testes: `deveRejeitarSolicitacaoNulaSemConsultarColaboradores` e
  `deveRejeitarEntradaInvalidaSemEfeitos` (14 casos parametrizados).
- Red: os 15 casos esperavam `IllegalArgumentException`, mas recebiam
  `UnsupportedOperationException` (`01-red.log`).
- Green: validar nulidade, textos, quantidade e preço antes de qualquer efeito.
  Neste incremento, entradas válidas ainda retornavam `null`; nenhum fluxo de
  compra foi antecipado (`01-green.log`: 15 casos passando).
- Refactor: extrair o método privado `validar`, preservando os 15 casos verdes
  (`01-refactor.log`).

## Ciclo 2 — compra aprovada e precisão

- Teste: `deveConfirmarCompraComTotalExato`, com três exemplos: quantidade mínima,
  `3 × 19.90 = 59.70` e `3 × 0.005 = 0.015`.
- Red: o resultado ainda era nulo; as asserções dos campos falharam com
  `NullPointerException` (`02-red.log`).
- Green: multiplicar com `BigDecimal`, reservar, cobrar, construir e salvar o
  pedido, notificar usando o número retornado e devolver o resultado
  (`02-green.log`: 18 casos passando).
- Refactor: nenhuma extração necessária naquele incremento; a sequência era
  curta e legível. Os testes usam uma fábrica de solicitação e o captor confere
  cada campo do pedido sem ocultar a configuração do cenário.

## Ciclo 3 — falta de estoque

- Teste: `deveRejeitarFaltaDeEstoqueSemCobrarOuLiberar`.
- Red: o serviço ignorava o retorno `false` da reserva e avançava para a cobrança,
  terminando com `NullPointerException` em vez de `EstoqueInsuficienteException`
  (`03-red.log`).
- Green: verificar o retorno de `reservar` e lançar a exceção antes dos outros
  colaboradores (`03-green.log`: 19 casos passando).
- Refactor: nenhuma necessária; o novo comportamento exigiu somente uma guarda.

## Incrementos seguintes

| Ciclo | Teste | Falha observada | Mudança para passar |
| --- | --- | --- | --- |
| 4 | `deveLiberarReservaAposPagamentoRecusado` | Nenhuma exceção era lançada após recusa. | Liberar e lançar `PagamentoRecusadoException`. |
| 5 | `deveLiberarReservaEPropagarMesmaFalhaDePagamento` | A chamada de liberação esperada não acontecia. | Capturar somente `FalhaPagamentoException`, liberar e relançar a mesma instância. |
| 6 | `deveManterCompraQuandoNotificacaoFalhar` | `FalhaNotificacaoException` escapava para o chamador. | Capturar essa falha e retornar o indicador `false`. |

Os logs `04` a `06` contêm os pares Red–Green desses incrementos. Não houve
necessidade de extrair métodos nesses três passos: manter os tratamentos junto
às operações facilita ver os limites de cada `try/catch`. A revisão final
organizou imports, removeu nomes de pacote repetidos e o comentário inicial
obsoleto. `07-refactor-final.log` confirma **22 casos, zero falhas e zero erros**.

## Desafio opcional

A suíte passou antes dos experimentos (`08-antes-desafio.log`). Cada alteração
foi aplicada isoladamente e desfeita após a execução.

| Alteração temporária | Teste que detectou | Log em `evidencias-tdd/` |
| --- | --- | --- |
| Notificar antes de salvar | `deveConfirmarCompraComTotalExato` e `deveManterCompraQuandoNotificacaoFalhar` | `09-notificar-antes-de-salvar.log` |
| Usar outro número na notificação | `deveConfirmarCompraComTotalExato` e `deveManterCompraQuandoNotificacaoFalhar` | `10-numero-incorreto.log` |
| Remover a liberação após recusa | `deveLiberarReservaAposPagamentoRecusado` | `11-sem-liberacao-na-recusa.log` |
| Arredondar `0.015` para duas casas | Caso de precisão de `deveConfirmarCompraComTotalExato` | `12-arredondamento-indevido.log` |

Todos os defeitos foram detectados, sem necessidade de acrescentar testes.
As alterações temporárias foram desfeitas. `13-restaurado.log` confirma os
22 casos passando com a implementação correta.

Para executar novamente: `mvn test`.
