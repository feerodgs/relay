package org.example.messageservice.service;

import org.example.messageservice.config.RabbitMQConfig;
import org.example.messageservice.dto.MessageRequest;
import org.example.messageservice.dto.MessageResponse;
import org.example.messageservice.grpc.UserGrpcClient;
import org.example.messageservice.grpc.UserResponse;
import org.example.messageservice.model.Message;
import org.example.messageservice.repository.MessageRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final RabbitTemplate rabbitTemplate;
    private final UserGrpcClient userGrpcClient;

    public MessageService(MessageRepository messageRepository,
                          RabbitTemplate rabbitTemplate,
                          UserGrpcClient userGrpcClient) {
        this.messageRepository = messageRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.userGrpcClient = userGrpcClient;
    }

    public MessageResponse createMessage(MessageRequest request, String senderId, String senderUsername) {
        Message message = new Message();
        message.setContent(request.content());
        message.setSenderId(senderId);
        message.setSenderUsername(senderUsername);

        Message saved = messageRepository.save(message);
        rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_NAME, saved.getId().toString());
        return toResponses(List.of(saved)).getFirst();
    }

    public List<MessageResponse> findAll() {
        return toResponses(messageRepository.findAll());
    }

    public List<MessageResponse> findBySenderId(String senderId) {
        return toResponses(messageRepository.findBySenderId(senderId));
    }

    public Optional<MessageResponse> findById(UUID id) {
        return messageRepository.findById(id)
                .map(message -> toResponses(List.of(message)).getFirst());
    }

    private List<MessageResponse> toResponses(List<Message> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }

        Set<String> senderUsernames = messages.stream()
                .map(Message::getSenderUsername)
                .collect(Collectors.toSet());
        Map<String, UserResponse> resolvedUsers = userGrpcClient.getUsersByUsernames(senderUsernames);

        return messages.stream()
                .map(message -> new MessageResponse(
                        message.getId(),
                        message.getContent(),
                        message.getSenderId(),
                        resolvedUsers.containsKey(message.getSenderUsername())
                                ? resolvedUsers.get(message.getSenderUsername()).getUsername()
                                : message.getSenderUsername(),
                        message.getCreatedAt()
                ))
                .toList();
    }
}
