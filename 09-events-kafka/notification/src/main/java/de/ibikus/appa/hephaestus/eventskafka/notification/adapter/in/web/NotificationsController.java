package de.ibikus.appa.hephaestus.eventskafka.notification.adapter.in.web;

import de.ibikus.appa.hephaestus.eventskafka.notification.application.port.in.NotificationQuery;
import de.ibikus.appa.hephaestus.eventskafka.notification.domain.Notification;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** What a customer has been told so far, oldest first. */
@RestController
class NotificationsController {

    private final NotificationQuery notifications;

    NotificationsController(NotificationQuery notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/api/notifications")
    List<Notification> forCustomer(@RequestParam String customerId) {
        return notifications.forCustomer(customerId);
    }
}
