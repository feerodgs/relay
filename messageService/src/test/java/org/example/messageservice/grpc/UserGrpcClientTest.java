package org.example.messageservice.grpc;

import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGrpcClientTest {

    @Mock
    private UserGrpcServiceGrpc.UserGrpcServiceBlockingStub stub;

    @Mock
    private OAuth2TokenProvider tokenProvider;

    private UserGrpcClient client;

    @BeforeEach
    void setUp() {
        client = new UserGrpcClient(stub, tokenProvider);
    }

    @Test
    void getUsersByUsernames_returnsEmptyMapWithoutCallingGrpc_whenNoUsernamesGiven() {
        Map<String, UserResponse> result = client.getUsersByUsernames(Set.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(tokenProvider, stub);
    }

    @Test
    void getUsersByUsernames_resolvesUsersKeyedByRequestedUsername() {
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        when(stub.withInterceptors(any())).thenReturn(stub);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);

        UserResponse alice = UserResponse.newBuilder()
                .setId("1").setUsername("alice").setEmail("a@x.com").build();
        UsersResponse grpcResponse = UsersResponse.newBuilder().addUsers(alice).build();
        when(stub.getUsersByUsernames(any())).thenReturn(grpcResponse);

        Map<String, UserResponse> result = client.getUsersByUsernames(Set.of("alice", "ghost"));

        assertThat(result).containsOnlyKeys("alice");
        assertThat(result.get("alice").getEmail()).isEqualTo("a@x.com");
    }

    @Test
    void getUsersByUsernames_returnsEmptyMap_whenGrpcCallFails() {
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        when(stub.withInterceptors(any())).thenReturn(stub);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.getUsersByUsernames(any()))
                .thenThrow(Status.UNAVAILABLE.asRuntimeException());

        Map<String, UserResponse> result = client.getUsersByUsernames(Set.of("alice"));

        assertThat(result).isEmpty();
    }

    @Test
    void getUsersByUsernames_returnsEmptyMap_whenTokenAcquisitionFails() {
        when(tokenProvider.getAccessToken()).thenThrow(new IllegalStateException("authService unreachable"));

        Map<String, UserResponse> result = client.getUsersByUsernames(Set.of("alice"));

        assertThat(result).isEmpty();
        verifyNoInteractions(stub);
    }
}
