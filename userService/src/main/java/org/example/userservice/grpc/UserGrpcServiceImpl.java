package org.example.userservice.grpc;

import io.grpc.stub.StreamObserver;
import org.example.userservice.model.User;
import org.example.userservice.repository.UserRepository;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class UserGrpcServiceImpl extends UserGrpcServiceGrpc.UserGrpcServiceImplBase {

    private final UserRepository userRepository;

    public UserGrpcServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void getUserByUsername(
            GetUserByUsernameRequest request,
            StreamObserver<UserResponse> responseObserver) {

        userRepository.findByUsername(request.getUsername())
                .ifPresentOrElse(
                        user -> {
                            UserResponse response = toUserResponse(user);
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        },
                        () -> responseObserver.onError(
                                io.grpc.Status.NOT_FOUND
                                        .withDescription("User not found: " + request.getUsername())
                                        .asRuntimeException()
                        )
                );
    }

    @Override
    public void getUsersByUsernames(
            GetUsersByUsernamesRequest request,
            StreamObserver<UsersResponse> responseObserver) {

        UsersResponse.Builder responseBuilder = UsersResponse.newBuilder();
        userRepository.findByUsernameIn(request.getUsernamesList())
                .forEach(user -> responseBuilder.addUsers(toUserResponse(user)));

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.newBuilder()
                .setId(user.getId().toString())
                .setUsername(user.getUsername())
                .setEmail(user.getEmail())
                .build();
    }
}