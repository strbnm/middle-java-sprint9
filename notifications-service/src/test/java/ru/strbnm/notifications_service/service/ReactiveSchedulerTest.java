package ru.strbnm.notifications_service.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import ru.strbnm.notifications_service.entity.Notification;
import ru.strbnm.notifications_service.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class ReactiveSchedulerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MeterRegistry meterRegistry;

    private ReactiveScheduler reactiveScheduler;

    @BeforeEach
    void setUp() {
        reactiveScheduler = new ReactiveScheduler(notificationRepository, 2, meterRegistry);
    }

    @Test
    void shouldMarkNotificationsAsSentAndSaveThem() {
        // given
        Notification n1 = Notification.builder()
                .id(1L)
                .email("test1@example.ru")
                .message("message1")
                .application("accounts-service")
                .isSent(false)
                .build();

        Notification n2= Notification.builder()
                .id(2L)
                .email("test2@example.ru")
                .message("message2")
                .application("cash-service")
                .isSent(false)
                .build();

        List<Notification> unsent = List.of(n1, n2);

        when(notificationRepository.findUnsentLimited(2)).thenReturn(Flux.fromIterable(unsent));
        when(notificationRepository.saveAll(anyList())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));

        // when + then
        StepVerifier.create(reactiveScheduler.processNotifications())
            .verifyComplete();

        assertTrue(n1.isSent());
        assertTrue(n2.isSent());

        verify(notificationRepository).findUnsentLimited(2);
        verify(notificationRepository).saveAll((Iterable<Notification>) argThat(list ->
            ((List<Notification>) list).stream().allMatch(Notification::isSent)
        ));
    }
}
