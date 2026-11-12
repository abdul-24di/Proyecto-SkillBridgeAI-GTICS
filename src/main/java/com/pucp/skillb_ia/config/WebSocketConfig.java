package com.pucp.skillb_ia.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;


@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String DESTINO_CHAT = "/user/queue/chat";

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accesor = StompHeaderAccessor.wrap(message);
                StompCommand comando = accesor.getCommand();

                // Solo usuarios con sesión iniciada
                if (StompCommand.CONNECT.equals(comando) && accesor.getUser() == null) {
                    throw new MessageDeliveryException("Debes iniciar sesión.");
                }
                // Nadie puede suscribirse a canales de otras personas ni a otros temas
                if (StompCommand.SUBSCRIBE.equals(comando) && !DESTINO_CHAT.equals(accesor.getDestination())) {
                    throw new MessageDeliveryException("Suscripción no permitida.");
                }

                if (StompCommand.SEND.equals(comando)) {
                    throw new MessageDeliveryException("Los mensajes se envían por la API REST.");
                }
                return message;
            }
        });
    }
}