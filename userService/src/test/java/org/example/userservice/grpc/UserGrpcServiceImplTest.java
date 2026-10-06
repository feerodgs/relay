package org.example.userservice.grpc;

import io.grpc.stub.StreamObserver;
import org.example.userservice.model.User;
import org.example.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGrpcServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StreamObserver<UsersResponse> responseObserver;

    private UserGrpcServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserGrpcServiceImpl(userRepository);
    }

    @Test
    void getUsersByUsernames_returnsOnlyMatchingUsers() {
        User alice = new User();
        alice.setId(UUID.randomUUID());
        alice.setUsername("alice");
        alice.setEmail("alice@example.com");

        User bob = new User();
        bob.setId(UUID.randomUUID());
        bob.setUsername("bob");
        bob.setEmail("bob@example.com");

        when(userRepository.findByUsernameIn(List.of("alice", "bob", "ghost")))
                .thenReturn(List.of(alice, bob));

        GetUsersByUsernamesRequest request = GetUsersByUsernamesRequest.newBuilder()
                .addAllUsernames(List.of("alice", "bob", "ghost"))
                .build();

        service.getUsersByUsernames(request, responseObserver);

        ArgumentCaptor<UsersResponse> captor = ArgumentCaptor.forClass(UsersResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();
        verify(responseObserver, never()).onError(org.mockito.ArgumentMatchers.any());

        List<String> returnedUsernames = captor.getValue().getUsersList().stream()
                .map(UserResponse::getUsername)
                .toList();
        assertThat(returnedUsernames).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void getUsersByUsernames_returnsEmptyResponse_whenNoneMatch() {
        when(userRepository.findByUsernameIn(List.of("ghost"))).thenReturn(List.of());

        GetUsersByUsernamesRequest request = GetUsersByUsernamesRequest.newBuilder()
                .addUsernames("ghost")
                .build();

        service.getUsersByUsernames(request, responseObserver);

        ArgumentCaptor<UsersResponse> captor = ArgumentCaptor.forClass(UsersResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();
        assertThat(captor.getValue().getUsersList()).isEmpty();
    }
}
