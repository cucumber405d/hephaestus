package de.ibikus.appa.hephaestus.servicecommunication.inventory.application.service;

import de.ibikus.appa.hephaestus.servicecommunication.inventory.application.port.in.ReservationResult;
import de.ibikus.appa.hephaestus.servicecommunication.inventory.application.port.in.ReserveStockUseCase;
import de.ibikus.appa.hephaestus.servicecommunication.inventory.application.port.out.ReservationRepository;
import de.ibikus.appa.hephaestus.servicecommunication.inventory.application.port.out.StockRepository;
import de.ibikus.appa.hephaestus.servicecommunication.inventory.domain.InvalidValueException;
import de.ibikus.appa.hephaestus.servicecommunication.inventory.domain.Reservation;
import de.ibikus.appa.hephaestus.servicecommunication.inventory.domain.ReservationLine;
import de.ibikus.appa.hephaestus.servicecommunication.inventory.domain.StockItem;
import de.ibikus.appa.hephaestus.servicecommunication.inventory.domain.UnknownSkuException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService implements ReserveStockUseCase {

    private final StockRepository stock;
    private final ReservationRepository reservations;
    private final Clock clock;

    public ReservationService(StockRepository stock, ReservationRepository reservations, Clock clock) {
        this.stock = stock;
        this.reservations = reservations;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReservationResult reserve(UUID orderId, List<ReservationLine> lines) {
        if (orderId == null) {
            throw new InvalidValueException("orderId is required");
        }
        if (lines == null || lines.isEmpty() || lines.size() > Reservation.MAX_LINES) {
            throw new InvalidValueException("A reservation needs 1.." + Reservation.MAX_LINES + " lines");
        }
        // The same SKU twice in one request counts once, with the quantities added up; sorted by SKU
        Map<String, Integer> wanted = new TreeMap<>();
        lines.forEach(line -> wanted.merge(line.sku(), line.quantity(), Integer::sum));

        // Lock first, then check for a previous reservation: a concurrent repeat of the same order waits on the
        // same rows and then finds the first one's reservation instead of reserving twice
        Map<String, StockItem> locked = stock.lockBySkus(wanted.keySet()).stream()
                .collect(Collectors.toMap(StockItem::sku, Function.identity()));
        var previous = reservations.findByOrderId(orderId);
        if (previous.isPresent()) {
            return new ReservationResult(previous.get(), false);
        }

        List<StockItem> updated = new ArrayList<>();
        List<ReservationLine> reservedLines = new ArrayList<>();
        wanted.forEach((sku, quantity) -> {
            var item = locked.get(sku);
            if (item == null) {
                throw new UnknownSkuException(sku);
            }
            updated.add(item.reserve(quantity));
            reservedLines.add(new ReservationLine(sku, quantity));
        });
        stock.updateReserved(updated);
        var reservation = new Reservation(UUID.randomUUID(), orderId, reservedLines, clock.instant());
        reservations.insert(reservation);
        return new ReservationResult(reservation, true);
    }
}
