package ru.strbnm.notifications_service.service;



import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import ru.strbnm.kafka.dto.NotificationMessage;
import ru.strbnm.notifications_service.entity.Notification;
import ru.strbnm.notifications_service.repository.NotificationRepository;

@ActiveProfiles("test")
@SpringBootTest
@EmbeddedKafka(topics = {"notifications"})
public class KafkaConsumerServiceTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private KafkaTemplate<String, NotificationMessage> kafkaTemplate;

    @Test
    void testProcessNotificationMessages() throws ExecutionException, InterruptedException {

        NotificationMessage accountsNotificationMessage = NotificationMessage.builder()
                .email("test@example.ru")
                .message("test message_accounts_service")
                .application("accounts-service")
                .build();

        NotificationMessage cashNotificationMessage = NotificationMessage.builder()
                .email("test@example.ru")
                .message("test message_cash_service")
                .application("cash-service")
                .build();

        kafkaTemplate.send("notifications", UUID.randomUUID().toString(), accountsNotificationMessage).get();
        kafkaTemplate.send("notifications", UUID.randomUUID().toString(), cashNotificationMessage).get();

        // ждём, пока сообщения обработаются и попадут в БД
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    List<Notification> all = notificationRepository.findAll().collectList().block();
                    assertThat(all).hasSize(2);
                    assertThat(all)
                            .anyMatch(n -> n.getMessage().equals("test message_accounts_service"))
                            .anyMatch(n -> n.getMessage().equals("test message_cash_service"));
                });
    }


}
