package org.example.messageservice.grpc;

import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class UserGrpcClient {

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final UserGrpcServiceGrpc.UserGrpcServiceBlockingStub stub;
    private final OAuth2TokenProvider tokenProvider;

    @Autowired
    public UserGrpcClient(GrpcChannelFactory channelFactory, OAuth2TokenProvider tokenProvider) {
        this.stub = UserGrpcServiceGrpc.newBlockingStub(
                channelFactory.createChannel("userService")
        );
        this.tokenProvider = tokenProvider;
    }

    UserGrpcClient(UserGrpcServiceGrpc.UserGrpcServiceBlockingStub stub, OAuth2TokenProvider tokenProvider) {
        this.stub = stub;
        this.tokenProvider = tokenProvider;
    }

    public Map<String, UserResponse> getUsersByUsernames(Set<String> usernames) {
        if (usernames.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            String token = tokenProvider.getAccessToken();
            Metadata headers = new Metadata();
            headers.put(AUTHORIZATION_KEY, "Bearer " + token);

            var stubWithAuth = stub.withInterceptors(
                    MetadataUtils.newAttachHeadersInterceptor(headers)
            );
            GetUsersByUsernamesRequest request = GetUsersByUsernamesRequest.newBuilder()
                    .addAllUsernames(usernames)
                    .build();
            UsersResponse response = stubWithAuth
                    .withDeadlineAfter(2, TimeUnit.SECONDS)
                    .getUsersByUsernames(request);

            Map<String, UserResponse> resolved = response.getUsersList().stream()
                    .collect(Collectors.toMap(UserResponse::getUsername, Function.identity(), (a, b) -> a));
            log.info("gRPC getUsersByUsernames({} requested) → {} resolved", usernames.size(), resolved.size());
            return resolved;
        } catch (StatusRuntimeException e) {
            log.error("gRPC getUsersByUsernames({}) failed: {}", usernames, e.getStatus(), e);
            return Collections.emptyMap();
        } catch (RuntimeException e) {
            log.error("Username batch resolution failed before the gRPC call: {}", usernames, e);
            return Collections.emptyMap();
        }
    }
}