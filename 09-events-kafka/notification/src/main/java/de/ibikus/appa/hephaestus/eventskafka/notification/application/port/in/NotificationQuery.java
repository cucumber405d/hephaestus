package de.ibikus.appa.hephaestus.eventskafka.notification.application.port.in;

import de.ibikus.appa.hephaestus.eventskafka.notification.domain.Notification;
import java.util.List;

public interface NotificationQuery {

    List<Notification> forCustomer(String customerId);
}
