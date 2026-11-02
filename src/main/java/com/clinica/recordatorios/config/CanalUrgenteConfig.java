package com.clinica.recordatorios.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.clinica.recordatorios.channel.ReminderChannel;
import com.clinica.recordatorios.channel.SmsReminderChannel;
import com.clinica.recordatorios.channel.gateway.NotificadorSmsGateway;

@Configuration
public class CanalUrgenteConfig {
    // El único "new" del proyecto para este canal vive aquí, en el composition root.
    @Bean
    public ReminderChannel canalUrgente(NotificadorSmsGateway gateway) {
        return new SmsReminderChannel(gateway);
    }
}