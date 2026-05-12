package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {

    @Autowired
    private PlayerService playerService;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // שליפת המידע ששמרנו ב-Session
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        Object quizIdObj = headerAccessor.getSessionAttributes().get("quizId");

        if (playerId != null && quizIdObj != null) {
            Long quizId = (quizIdObj instanceof Integer) ? ((Integer) quizIdObj).longValue() : (Long) quizIdObj;
            System.out.println("שחקן התנתק: " + playerId + " מחידון: " + quizId);
            playerService.removePlayer(quizId, playerId);
        }
    }
}