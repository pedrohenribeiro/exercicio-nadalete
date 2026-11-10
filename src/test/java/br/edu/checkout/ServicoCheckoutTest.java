package br.edu.checkout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ServicoCheckoutTest {

    @Mock
    private EstoqueGateway estoqueGateway;

    @Mock
    private PagamentoGateway pagamentoGateway;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private Notificador notificador;

    private ServicoCheckout servico;

    @BeforeEach
    void criarServicoReal() {
        servico = new ServicoCheckout(
                estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    @Test
    void deveRejeitarSolicitacaoNulaSemConsultarColaboradores() {
        assertThrows(IllegalArgumentException.class, () -> servico.finalizar(null));
        verifyNoInteractions(
                estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    @ParameterizedTest
    @MethodSource("entradasInvalidas")
    void deveRejeitarEntradaInvalidaSemEfeitos(SolicitacaoCheckout entrada) {
        assertThrows(IllegalArgumentException.class, () -> servico.finalizar(entrada));
        verifyNoInteractions(estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    static Stream<SolicitacaoCheckout> entradasInvalidas() {
        var entradas = new ArrayList<SolicitacaoCheckout>();
        for (String texto : new String[] {null, "", "   "}) {
            entradas.add(new SolicitacaoCheckout(texto, "ana@email.com", "SKU-1", 3, new BigDecimal("19.90")));
            entradas.add(new SolicitacaoCheckout("cliente-1", texto, "SKU-1", 3, new BigDecimal("19.90")));
            entradas.add(new SolicitacaoCheckout("cliente-1", "ana@email.com", texto, 3, new BigDecimal("19.90")));
        }
        for (int quantidade : new int[] {0, -1}) {
            entradas.add(solicitacao(quantidade, new BigDecimal("19.90")));
        }
        for (BigDecimal preco : new BigDecimal[] {null, BigDecimal.ZERO, new BigDecimal("-0.01")}) {
            entradas.add(solicitacao(3, preco));
        }
        return entradas.stream();
    }

    private static SolicitacaoCheckout solicitacao(int quantidade, BigDecimal preco) {
        return new SolicitacaoCheckout("cliente-1", "ana@email.com", "SKU-1", quantidade, preco);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 19.90, 19.90", "3, 19.90, 59.70", "3, 0.005, 0.015"
    })
    void deveConfirmarCompraComTotalExato(int quantidade, String preco, String valorTotal) {
        var entrada = solicitacao(quantidade, new BigDecimal(preco));
        var total = new BigDecimal(valorTotal);
        when(estoqueGateway.reservar("SKU-1", quantidade)).thenReturn(true);
        when(pagamentoGateway.cobrar("cliente-1", total)).thenReturn(new Pagamento(true, "tx-42"));
        when(pedidoRepository.salvar(any(PedidoConfirmado.class))).thenReturn("pedido-73");

        var resultado = servico.finalizar(entrada);

        assertAll(
                () -> assertEquals("pedido-73", resultado.numeroPedido()),
                () -> assertEquals(total, resultado.total()),
                () -> assertTrue(resultado.notificacaoEnviada()));
        var captor = ArgumentCaptor.forClass(PedidoConfirmado.class);
        InOrder ordem = inOrder(estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
        ordem.verify(estoqueGateway, times(1)).reservar("SKU-1", quantidade);
        ordem.verify(pagamentoGateway, times(1)).cobrar("cliente-1", total);
        ordem.verify(pedidoRepository, times(1)).salvar(captor.capture());
        ordem.verify(notificador, times(1)).enviarConfirmacao("ana@email.com", "pedido-73", total);
        var pedido = captor.getValue();
        assertAll(
                () -> assertEquals(entrada.clienteId(), pedido.clienteId()),
                () -> assertEquals(entrada.email(), pedido.email()),
                () -> assertEquals(entrada.sku(), pedido.sku()),
                () -> assertEquals(quantidade, pedido.quantidade()),
                () -> assertEquals(total, pedido.total()),
                () -> assertEquals("tx-42", pedido.transacaoId()));
        verify(estoqueGateway, never()).liberar(anyString(), anyInt());
        verifyNoMoreInteractions(estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    @Test
    void deveRejeitarFaltaDeEstoqueSemCobrarOuLiberar() {
        when(estoqueGateway.reservar("SKU-1", 3)).thenReturn(false);

        assertThrows(EstoqueInsuficienteException.class,
                () -> servico.finalizar(solicitacao(3, new BigDecimal("19.90"))));

        verify(estoqueGateway, times(1)).reservar("SKU-1", 3);
        verify(estoqueGateway, never()).liberar(anyString(), anyInt());
        verifyNoInteractions(pagamentoGateway, pedidoRepository, notificador);
        verifyNoMoreInteractions(estoqueGateway);
    }

    @Test
    void deveLiberarReservaAposPagamentoRecusado() {
        var total = new BigDecimal("59.70");
        when(estoqueGateway.reservar("SKU-1", 3)).thenReturn(true);
        when(pagamentoGateway.cobrar("cliente-1", total)).thenReturn(new Pagamento(false, null));

        assertThrows(PagamentoRecusadoException.class,
                () -> servico.finalizar(solicitacao(3, new BigDecimal("19.90"))));

        InOrder ordem = inOrder(estoqueGateway, pagamentoGateway);
        ordem.verify(estoqueGateway, times(1)).reservar("SKU-1", 3);
        ordem.verify(pagamentoGateway, times(1)).cobrar("cliente-1", total);
        ordem.verify(estoqueGateway, times(1)).liberar("SKU-1", 3);
        verifyNoInteractions(pedidoRepository, notificador);
        verifyNoMoreInteractions(estoqueGateway, pagamentoGateway);
    }

    @Test
    void deveLiberarReservaEPropagarMesmaFalhaDePagamento() {
        var total = new BigDecimal("59.70");
        var falha = new FalhaPagamentoException("Gateway indisponível");
        when(estoqueGateway.reservar("SKU-1", 3)).thenReturn(true);
        when(pagamentoGateway.cobrar("cliente-1", total)).thenThrow(falha);

        var recebida = assertThrows(FalhaPagamentoException.class,
                () -> servico.finalizar(solicitacao(3, new BigDecimal("19.90"))));

        assertSame(falha, recebida);
        InOrder ordem = inOrder(estoqueGateway, pagamentoGateway);
        ordem.verify(estoqueGateway, times(1)).reservar("SKU-1", 3);
        ordem.verify(pagamentoGateway, times(1)).cobrar("cliente-1", total);
        ordem.verify(estoqueGateway, times(1)).liberar("SKU-1", 3);
        verifyNoInteractions(pedidoRepository, notificador);
        verifyNoMoreInteractions(estoqueGateway, pagamentoGateway);
    }

    @Test
    void deveManterCompraQuandoNotificacaoFalhar() {
        var total = new BigDecimal("59.70");
        var pedido = new PedidoConfirmado("cliente-1", "ana@email.com", "SKU-1", 3, total, "tx-42");
        when(estoqueGateway.reservar("SKU-1", 3)).thenReturn(true);
        when(pagamentoGateway.cobrar("cliente-1", total)).thenReturn(new Pagamento(true, "tx-42"));
        when(pedidoRepository.salvar(pedido)).thenReturn("pedido-73");
        doThrow(new FalhaNotificacaoException("Servidor indisponível"))
                .when(notificador).enviarConfirmacao("ana@email.com", "pedido-73", total);

        var resultado = servico.finalizar(solicitacao(3, new BigDecimal("19.90")));

        assertEquals(new ResultadoCheckout("pedido-73", total, false), resultado);
        InOrder ordem = inOrder(estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
        ordem.verify(estoqueGateway, times(1)).reservar("SKU-1", 3);
        ordem.verify(pagamentoGateway, times(1)).cobrar("cliente-1", total);
        ordem.verify(pedidoRepository, times(1)).salvar(pedido);
        ordem.verify(notificador, times(1)).enviarConfirmacao("ana@email.com", "pedido-73", total);
        verify(estoqueGateway, never()).liberar(anyString(), anyInt());
        verifyNoMoreInteractions(estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }
}
