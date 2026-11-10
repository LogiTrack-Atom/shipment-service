package com.logitrack_atom.shipment_service.assignment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.rabbitmq.client.Channel;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;

/** Comprueba orden commit/ACK y decisiones de retry con límites de transporte controlados. */
class AssignmentResultListenerTest {
    private final AssignmentResultsService service = mock(AssignmentResultsService.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final Channel channel = mock(Channel.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final AssignmentResultListener listener = new AssignmentResultListener(service, mapper, rabbit);
    private final Message message = AssignmentResultFixtures.message(AssignmentResultFixtures.body(UUID.randomUUID()), mapper);

    @Test
    void ackHappensAfterServiceReturns() throws Exception {
        listener.onMessage(message, channel);
        var order = inOrder(service, channel);
        order.verify(service).process(any(), any());
        order.verify(channel).basicAck(1, false);
        verifyNoInteractions(rabbit);
    }

    @Test
    void invalidContractRejectsWithoutCallingSqlOrRetry() throws Exception {
        message.getMessageProperties().setMessageId("invalid");
        listener.onMessage(message, channel);
        verify(channel).basicReject(1, false);
        verifyNoInteractions(service, rabbit);
    }

    @Test
    void transientFailureConfirmsPersistentRetryBeforeAck() throws Exception {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("fixture")).when(service).process(any(), any());
        doAnswer(call -> {
            Message retry = call.getArgument(2);
            assertThat(retry.getBody()).isEqualTo(message.getBody());
            assertThat(retry.getMessageProperties().getHeaders().get("x-retry-count")).isEqualTo(1);
            assertThat(retry.getMessageProperties().getMessageId()).isEqualTo(message.getMessageProperties().getMessageId());
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
            call.<CorrelationData>getArgument(3).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq("lt.assignment.retry.v1"), eq("shipment.retry"), any(Message.class), any(CorrelationData.class));
        listener.onMessage(message, channel);
        verify(channel).basicAck(1, false);
    }

    @Test
    void exhaustedRetryRejectsWithoutRequeue() throws Exception {
        message.getMessageProperties().setHeader("x-retry-count", 3);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("fixture")).when(service).process(any(), any());
        listener.onMessage(message, channel);
        verify(channel).basicReject(1, false);
        verifyNoInteractions(rabbit);
    }

    @Test
    void lostAckPropagatesWithoutRepublishingProcessedCommand() throws Exception {
        doThrow(new java.io.IOException("fixture")).when(channel).basicAck(1, false);
        assertThatThrownBy(() -> listener.onMessage(message, channel)).isInstanceOf(java.io.IOException.class);
        verify(service).process(any(), any());
        verifyNoInteractions(rabbit);
    }
}
