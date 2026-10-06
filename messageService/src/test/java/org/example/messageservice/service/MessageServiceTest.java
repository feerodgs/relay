package org.example.messageservice.service;

import org.example.messageservice.config.RabbitMQConfig;
import org.example.messageservice.dto.MessageRequest;
import org.example.messageservice.dto.MessageResponse;
import org.example.messageservice.grpc.UserGrpcClient;
import org.example.messageservice.grpc.UserResponse;
import org.example.messageservice.model.Message;
import org.example.messageservice.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private UserGrpcClient userGrpcClient;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(messageRepository, rabbitTemplate, userGrpcClient);
    }

    private Message messageFrom(String senderUsername) {
        Message message = new Message();
        message.setContent("hi");
        message.setSenderId(UUID.randomUUID().toString());
        message.setSenderUsername(senderUsername);
        return message;
    }

    @Test
    void findAll_resolvesAllSenderUsernamesInASingleGrpcCall() {
        Message fromAlice1 = messageFrom("alice");
        Message fromAlice2 = messageFrom("alice");
        Message fromBob = messageFrom("bob");
        when(messageRepository.findAll()).thenReturn(List.of(fromAlice1, fromAlice2, fromBob));

        UserResponse alice = UserResponse.newBuilder().setUsername("alice-updated").build();
        UserResponse bob = UserResponse.newBuilder().setUsername("bob").build();
        when(userGrpcClient.getUsersByUsernames(Set.of("alice", "bob")))
                .thenReturn(Map.of("alice", alice, "bob", bob));

        List<MessageResponse> result = messageService.findAll();

        assertThat(result).extracting(MessageResponse::senderUsername)
                .containsExactly("alice-updated", "alice-updated", "bob");
        verify(userGrpcClient, times(1)).getUsersByUsernames(any());
    }

    @Test
    void findAll_fallsBackToStoredUsername_whenGrpcDoesNotResolveIt() {
        Message fromGhost = messageFrom("ghost");
        when(messageRepository.findAll()).thenReturn(List.of(fromGhost));
        when(userGrpcClient.getUsersByUsernames(Set.of("ghost"))).thenReturn(Map.of());

        List<MessageResponse> result = messageService.findAll();

        assertThat(result).extracting(MessageResponse::senderUsername).containsExactly("ghost");
    }

    @Test
    void findAll_doesNotCallGrpc_whenThereAreNoMessages() {
        when(messageRepository.findAll()).thenReturn(List.of());

        List<MessageResponse> result = messageService.findAll();

        assertThat(result).isEmpty();
        verifyNoInteractions(userGrpcClient);
    }

    @Test
    void createMessage_resolvesSenderUsernameForTheSavedMessage() {
        MessageRequest request = new MessageRequest("hello");
        Message saved = messageFrom("alice");
        saved.setId(UUID.randomUUID());
        when(messageRepository.save(any(Message.class))).thenReturn(saved);
        when(userGrpcClient.getUsersByUsernames(Set.of("alice")))
                .thenReturn(Map.of("alice", UserResponse.newBuilder().setUsername("alice").build()));

        MessageResponse response = messageService.createMessage(request, saved.getSenderId(), "alice");

        assertThat(response.senderUsername()).isEqualTo("alice");
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.QUEUE_NAME), eq(saved.getId().toString()));
    }
}
