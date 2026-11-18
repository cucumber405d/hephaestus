package de.ibikus.appa.hephaestus.eventskafka.notification.adapter.in.web;

import de.ibikus.appa.hephaestus.eventskafka.notification.adapter.in.messaging.DltReplayer;
import de.ibikus.appa.hephaestus.eventskafka.notification.application.service.Faults;
import de.ibikus.appa.hephaestus.eventskafka.notification.application.service.NotificationService;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Experiment controls of this instance: faults, statistics (with its partitions), replay of the dead letter topic. */
@RestController
@RequestMapping("/admin")
class AdminController {

    private final NotificationService service;
    private final DltReplayer replayer;
    private final KafkaListenerEndpointRegistry listeners;
    private final String mode;

    AdminController(
            NotificationService service,
            DltReplayer replayer,
            KafkaListenerEndpointRegistry listeners,
            @Value("${notification.mode:classic}") String mode) {
        this.service = service;
        this.replayer = replayer;
        this.listeners = listeners;
        this.mode = mode;
    }

    @PutMapping("/faults")
    Faults setFaults(@RequestBody Faults faults) {
        service.faults(faults);
        return faults;
    }

    @DeleteMapping("/faults")
    ResponseEntity<Void> clearFaults() {
        service.faults(Faults.NONE);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    Stats stats() {
        return new Stats(hostname(), mode, service.processed(), service.duplicates(), partitions());
    }

    @PostMapping("/dlt/replay")
    Replayed replay() {
        return new Replayed(replayer.replay());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    /** Partitions this instance owns in the classic group (share group members own none). */
    private List<Integer> partitions() {
        var container = listeners.getListenerContainer("notification");
        if (container == null || container.getAssignedPartitions() == null) {
            return List.of();
        }
        return container.getAssignedPartitions().stream()
                .map(tp -> tp.partition())
                .sorted()
                .toList();
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    record Stats(String instance, String mode, long processed, long duplicates, List<Integer> partitions) {}

    record Replayed(int replayed) {}
}
