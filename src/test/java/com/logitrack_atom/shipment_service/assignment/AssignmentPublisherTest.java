package com.logitrack_atom.shipment_service.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Comprueba decisiones ante confirm/return/fallo; la aceptación del broker es un ensayo distinto. */
class AssignmentPublisherTest {
    private final OutboxStore outbox = mock(OutboxStore.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final OutboxStore.Pending row = new OutboxStore.Pending(UUID.randomUUID(), UUID.randomUUID(), "availability.request", "{}", UUID.randomUUID(), 1);
    private final AssignmentPublisher publisher = new AssignmentPublisher(outbox, rabbit);

    @BeforeEach
    void pending() { when(outbox.claim()).thenReturn(Optional.of(row), Optional.empty()); }

    @Test
    void positiveConfirmMarksOnlyAfterAckAndWithoutSqlTransaction() {
        doAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Message message = call.getArgument(2);
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo(row.messageId().toString());
            assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo(row.operationId().toString());
            assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
            verify(outbox, never()).confirmed(any());
            call.<CorrelationData>getArgument(3).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publisher.publishPending();
        verify(outbox).confirmed(row);
        verify(outbox, never()).failed(any());
    }

    @Test
    void brokerFailureKeepsMessagePending() {
        doThrow(new IllegalStateException("fixture")).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publisher.publishPending();
        verify(outbox).failed(row);
        verify(outbox, never()).confirmed(any());
    }

    @Test
    void nackKeepsMessagePending() {
        doAnswer(call -> { call.<CorrelationData>getArgument(3).getFuture().complete(new CorrelationData.Confirm(false, "fixture")); return null; })
                .when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publisher.publishPending();
        verify(outbox).failed(row);
        verify(outbox, never()).confirmed(any());
    }

    @Test
    void mandatoryReturnWinsOverPositiveConfirm() {
        doAnswer(call -> {
            CorrelationData data = call.getArgument(3);
            data.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()), 312, "NO_ROUTE", "fixture", "fixture"));
            data.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publisher.publishPending();
        verify(outbox).failed(row);
        verify(outbox, never()).confirmed(any());
    }
}
