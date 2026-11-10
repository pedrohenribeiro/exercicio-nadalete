# Explicação da solução

O serviço coordena quatro etapas: reservar estoque, cobrar, salvar o pedido e
enviar a confirmação. A validação acontece antes de qualquer uma dessas etapas.
O total usa `BigDecimal.multiply`, preservando a precisão, inclusive para preços
com mais de duas casas decimais.

## O que foi mockado e por quê?

Somente `EstoqueGateway`, `PagamentoGateway`, `PedidoRepository` e `Notificador`:
representam sistemas externos. Os mocks permitem controlar respostas e falhas e
observar efeitos sem banco de dados ou rede. O serviço, os records e os valores
`BigDecimal` são objetos reais.

## Estado e interação

O teste `deveConfirmarCompraComTotalExato` verifica estado ao conferir os campos
de `ResultadoCheckout`. O teste `deveLiberarReservaAposPagamentoRecusado`
verifica interação ao conferir que `liberar` é chamado uma única vez depois da
cobrança. Um mesmo teste pode realizar os dois tipos de verificação.

## Que defeito o captor revela?

O `ArgumentCaptor<PedidoConfirmado>` permite conferir os seis campos enviados ao
repositório. Ele revela, por exemplo, um pedido salvo com quantidade incorreta,
total errado ou identificador de transação diferente do retornado pelo gateway.
Verificar apenas `salvar(any())` deixaria esses defeitos passarem.

## Por que verificar a ordem?

A ordem é parte do contrato do exercício: a cobrança depende da reserva, o
registro depende da aprovação e a confirmação usa o número do pedido salvo.
Notificar antes de salvar poderia anunciar uma compra ainda não registrada.
Após recusa ou falha técnica da cobrança, liberar a reserva é a compensação
exigida. Já uma falha de notificação apenas produz o indicador `false`, pois a
compra já foi aprovada e registrada.
