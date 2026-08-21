package com.learnflow.service;

import com.learnflow.entity.User;

public interface PasswordResetDelivery {
    void send(User user, String rawToken);
}
