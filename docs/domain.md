# Домен «Мастерская Гефеста»

Мастерская продаёт изделия со склада и куёт на заказ. Код и термины — английский, документация — русский.

| Контекст | Ответственность | Ключевые понятия | Подпроекты |
|---|---|---|---|
| Catalog | Изделия, цены | `Item`, `Price`, `Category` | 02–04 |
| Ordering | Жизненный цикл заказа | `Order`, `OrderLine`, `OrderStatus` | 06, 09, 10, 16 |
| Inventory | Остатки, резервирование | `StockItem`, `Reservation` | 06, 08, 10 |
| Forge | Изготовление на заказ | `WorkOrder`, `Material`, `Smith` | 01, 13, 17 |
| Payment | Оплата, возвраты (fake-провайдер) | `Payment`, `Refund` | 10, 17 |
| Delivery | Отправка | `Shipment`, `Courier` | 10 |
| Notification | Уведомления | `Notification`, `Channel` | 09, 11 |
| Customer | Профиль клиента (auth — Keycloak) | `Customer` | 07 |
| Supplier (внешний) | Нестабильный поставщик материалов | — | 01, 08, 13 |

**Карта контекстов**
- Catalog → Ordering: upstream / conformist.
- Ordering — центр саги. Хореография: `OrderPlaced` → Payment, Inventory → `PaymentAuthorized`, `StockReserved` → Delivery. Оркестрация (10): `OrderSaga` в Ordering.
- Supplier → Inventory/Forge через Anti-Corruption Layer.
- Роли Keycloak: `customer`, `smith`, `admin`.

**Доменные события:** `OrderPlaced`, `OrderCancelled`, `PaymentAuthorized`, `PaymentFailed`, `StockReserved`,
`StockReservationFailed`, `WorkOrderCompleted`, `ShipmentDispatched`.

Подпроект берёт нужный срез и может упрощать модель под свою тему.
