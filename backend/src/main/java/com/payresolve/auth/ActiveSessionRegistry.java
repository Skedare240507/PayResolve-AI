package com.payresolve.auth;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active authenticated HTTP sessions per username so password resets can sign out
 * all existing sessions for that user.
 */
@Component
public class ActiveSessionRegistry implements HttpSessionListener {

    private static final String USERNAME_ATTR = "PAYRESOLVE_AUTH_USERNAME";

    private final ConcurrentHashMap<String, Set<HttpSession>> userSessions = new ConcurrentHashMap<>();

    public void registerSession(String username, HttpSession session) {
        if (username == null || session == null) {
            return;
        }
        String key = username.toLowerCase();
        session.setAttribute(USERNAME_ATTR, key);
        userSessions.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregisterSession(HttpSession session) {
        if (session == null) {
            return;
        }
        Object attr;
        try {
            attr = session.getAttribute(USERNAME_ATTR);
        } catch (IllegalStateException ignored) {
            return;
        }
        if (attr instanceof String key) {
            Set<HttpSession> sessions = userSessions.get(key);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(key, sessions);
                }
            }
        }
    }

    public int invalidateSessionsForUser(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        Set<HttpSession> sessions = userSessions.remove(username.toLowerCase());
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        int invalidated = 0;
        for (HttpSession session : sessions) {
            try {
                session.invalidate();
                invalidated++;
            } catch (IllegalStateException ignored) {
                // Already invalidated
            }
        }
        return invalidated;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        unregisterSession(se.getSession());
    }
}
