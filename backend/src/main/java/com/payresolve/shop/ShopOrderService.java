package com.payresolve.shop;

import com.payresolve.common.Money;
import com.payresolve.common.ResourceNotFoundException;
import com.payresolve.common.ValidationException;
import com.payresolve.paypal.PayPalOrdersClient;
import com.payresolve.paypal.model.PayPalOrder;
import com.payresolve.shop.dto.CaptureOrderResponseDto;
import com.payresolve.shop.dto.CheckoutResponseDto;
import com.payresolve.shop.dto.OrderItemRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing shop catalog, order lifecycle, PayPal checkout/capture,
 * refunds, and simulated shipments.
 */
@Service
public class ShopOrderService {

    private static final Logger log = LoggerFactory.getLogger(ShopOrderService.class);

    private final JdbcClient jdbcClient;
    private final Clock clock;
    private final PayPalOrdersClient payPalOrdersClient;
    private final DemoFaultSwitches faultSwitches;
    /**
     * The API's own public URL — used as a fallback APP_PUBLIC_URL.
     * Not used for PayPal return/cancel URLs; see frontendOrigin.
     */
    private final String publicUrl;

    /**
     * The UI's public origin — used to build PayPal return_url and cancel_url
     * so that after PayPal approval the buyer lands on the shop UI, not the API.
     * Corresponds to FRONTEND_ORIGIN / payresolve.frontend-origin.
     */
    private final String frontendOrigin;

    public ShopOrderService(
            JdbcClient jdbcClient,
            Clock clock,
            PayPalOrdersClient payPalOrdersClient,
            DemoFaultSwitches faultSwitches,
            @Value("${payresolve.public-url:http://localhost:8080}") String publicUrl,
            @Value("${payresolve.frontend-origin:http://localhost:5173}") String frontendOrigin
    ) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
        this.payPalOrdersClient = payPalOrdersClient;
        this.faultSwitches = faultSwitches;
        this.publicUrl = publicUrl;
        this.frontendOrigin = frontendOrigin;
    }

    // =========================================================================
    // Product Catalog
    // =========================================================================

    public List<Product> findAllProducts() {
        return jdbcClient.sql("""
            SELECT id, name, price_minor, currency, description, created_at
            FROM products
            ORDER BY price_minor ASC
            """)
            .query((rs, rowNum) -> new Product(
                rs.getString("id"),
                rs.getString("name"),
                rs.getLong("price_minor"),
                rs.getString("currency"),
                rs.getString("description"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null
            ))
            .list();
    }

    public Optional<Product> findProductById(String id) {
        Objects.requireNonNull(id, "Product id must not be null");
        return jdbcClient.sql("""
            SELECT id, name, price_minor, currency, description, created_at
            FROM products
            WHERE id = :id
            """)
            .param("id", id)
            .query((rs, rowNum) -> new Product(
                rs.getString("id"),
                rs.getString("name"),
                rs.getLong("price_minor"),
                rs.getString("currency"),
                rs.getString("description"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null
            ))
            .optional();
    }

    // =========================================================================
    // Order Management
    // =========================================================================

    public Optional<ShopOrder> findOrderById(String id) {
        Objects.requireNonNull(id, "Order id must not be null");
        return jdbcClient.sql("""
            SELECT id, paypal_order_id, status, total_minor, currency, customer_label, created_at, updated_at
            FROM shop_orders
            WHERE id = :id
            """)
            .param("id", id)
            .query((rs, rowNum) -> new ShopOrder(
                rs.getString("id"),
                rs.getString("paypal_order_id"),
                rs.getString("status"),
                rs.getLong("total_minor"),
                rs.getString("currency"),
                rs.getString("customer_label"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null
            ))
            .optional();
    }

    public Optional<ShopOrder> findOrderByPayPalOrderId(String paypalOrderId) {
        if (paypalOrderId == null || paypalOrderId.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient.sql("""
            SELECT id, paypal_order_id, status, total_minor, currency, customer_label, created_at, updated_at
            FROM shop_orders
            WHERE paypal_order_id = :paypalOrderId
            """)
            .param("paypalOrderId", paypalOrderId)
            .query((rs, rowNum) -> new ShopOrder(
                rs.getString("id"),
                rs.getString("paypal_order_id"),
                rs.getString("status"),
                rs.getLong("total_minor"),
                rs.getString("currency"),
                rs.getString("customer_label"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null
            ))
            .optional();
    }

    public List<ShopOrder> findAllOrders(int limit, int offset) {
        return jdbcClient.sql("""
            SELECT id, paypal_order_id, status, total_minor, currency, customer_label, created_at, updated_at
            FROM shop_orders
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
            """)
            .param("limit", limit)
            .param("offset", offset)
            .query((rs, rowNum) -> new ShopOrder(
                rs.getString("id"),
                rs.getString("paypal_order_id"),
                rs.getString("status"),
                rs.getLong("total_minor"),
                rs.getString("currency"),
                rs.getString("customer_label"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null
            ))
            .list();
    }

    @Transactional
    public ShopOrder createOrderFromCart(List<OrderItemRequest> items, String customerLabel, String currency) {
        if (items == null || items.isEmpty()) {
            throw new ValidationException("Order must contain at least one item");
        }

        String orderCurrency = currency != null && !currency.isBlank() ? currency.toUpperCase() : "USD";
        long computedTotalMinor = 0L;

        for (OrderItemRequest item : items) {
            if (item.quantity() <= 0) {
                throw new ValidationException("Item quantity must be greater than zero for product: " + item.productId());
            }
            Product product = findProductById(item.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + item.productId()));
            computedTotalMinor += product.priceMinor() * item.quantity();
        }

        // Demo Fault Switch: Alter order total
        if (faultSwitches.isAlterOrderTotal()) {
            log.warn("Demo fault switch ACTIVE: Altering order total by +10.00 USD");
            computedTotalMinor += 1000L; // Add $10.00 to force discrepancy with PayPal capture
        }

        String orderId = "ORD-" + (10480 + (int) (Math.random() * 9000));
        Instant now = clock.instant();
        String label = customerLabel != null && !customerLabel.isBlank() ? customerLabel : "Demo Customer";

        jdbcClient.sql("""
            INSERT INTO shop_orders (id, paypal_order_id, status, total_minor, currency, customer_label, created_at, updated_at)
            VALUES (:id, NULL, 'PENDING', :totalMinor, :currency, :customerLabel, :createdAt, :updatedAt)
            """)
            .param("id", orderId)
            .param("totalMinor", computedTotalMinor)
            .param("currency", orderCurrency)
            .param("customerLabel", label)
            .param("createdAt", Timestamp.from(now))
            .param("updatedAt", Timestamp.from(now))
            .update();

        log.info("Created shop order {} for total={} {} (customer={})", orderId, computedTotalMinor, orderCurrency, label);

        // Persist cart items into shop_order_items
        long itemsSumMinor = 0L;
        for (OrderItemRequest item : items) {
            Product product = findProductById(item.productId()).orElseThrow();
            jdbcClient.sql("""
                INSERT INTO shop_order_items (shop_order_id, product_id, quantity, unit_price_minor)
                VALUES (:shopOrderId, :productId, :quantity, :unitPriceMinor)
                """)
                .param("shopOrderId", orderId)
                .param("productId", product.id())
                .param("quantity", item.quantity())
                .param("unitPriceMinor", product.priceMinor())
                .update();
            itemsSumMinor += (long) item.quantity() * product.priceMinor();
        }

        // Verify that shop_orders.total_minor matches the sum of its items (unless the alter-order-total fault switch is active)
        if (!faultSwitches.isAlterOrderTotal() && computedTotalMinor != itemsSumMinor) {
            throw new IllegalStateException("Order total " + computedTotalMinor + " does not match item sum " + itemsSumMinor);
        }

        return findOrderById(orderId).orElseThrow();
    }

    public List<ShopOrderItem> findOrderItems(String shopOrderId) {
        if (shopOrderId == null || shopOrderId.isBlank()) {
            return List.of();
        }
        return jdbcClient.sql("""
            SELECT id, shop_order_id, product_id, quantity, unit_price_minor
            FROM shop_order_items
            WHERE shop_order_id = :shopOrderId
            ORDER BY id ASC
            """)
            .param("shopOrderId", shopOrderId)
            .query((rs, rowNum) -> new ShopOrderItem(
                rs.getLong("id"),
                rs.getString("shop_order_id"),
                rs.getString("product_id"),
                rs.getInt("quantity"),
                rs.getLong("unit_price_minor")
            ))
            .list();
    }

    public boolean verifyOrderTotalMatchesItems(String shopOrderId) {
        ShopOrder order = findOrderById(shopOrderId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop order not found: " + shopOrderId));
        List<ShopOrderItem> items = findOrderItems(shopOrderId);
        long itemsSum = items.stream().mapToLong(i -> (long) i.quantity() * i.unitPriceMinor()).sum();
        return order.totalMinor() == itemsSum;
    }

    @Transactional
    public void createOrder(String id, String paypalOrderId, Money total, String customerLabel) {
        Instant now = clock.instant();
        jdbcClient.sql("""
            INSERT INTO shop_orders (id, paypal_order_id, status, total_minor, currency, customer_label, created_at, updated_at)
            VALUES (:id, :paypalOrderId, 'PENDING', :totalMinor, :currency, :customerLabel, :createdAt, :updatedAt)
            """)
            .param("id", id)
            .param("paypalOrderId", paypalOrderId)
            .param("totalMinor", total.amountMinor())
            .param("currency", total.currency())
            .param("customerLabel", customerLabel)
            .param("createdAt", Timestamp.from(now))
            .param("updatedAt", Timestamp.from(now))
            .update();
    }

    // =========================================================================
    // PayPal Checkout & Capture
    // =========================================================================

    @Transactional
    public CheckoutResponseDto initiatePayPalCheckout(String shopOrderId, String returnUrl, String cancelUrl) {
        ShopOrder order = findOrderById(shopOrderId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop order not found: " + shopOrderId));

        // Build return/cancel URLs using the UI origin so the buyer lands on the
        // shop UI pages after PayPal approval, not on the API server.
        // The shop UI handles /shop/return and /shop/cancel and reads query params.
        String effectiveReturnUrl = (returnUrl != null && !returnUrl.isBlank()) ? returnUrl : (frontendOrigin + "/shop/return");
        String effectiveCancelUrl = (cancelUrl != null && !cancelUrl.isBlank()) ? cancelUrl : (frontendOrigin + "/shop/cancel");

        PayPalOrder payPalOrder = payPalOrdersClient.createOrder(
            order.id(),
            Money.of(order.totalMinor(), order.currency()),
            effectiveReturnUrl,
            effectiveCancelUrl
        );

        Instant now = clock.instant();
        jdbcClient.sql("""
            UPDATE shop_orders
            SET paypal_order_id = :paypalOrderId, updated_at = :updatedAt
            WHERE id = :id
            """)
            .param("paypalOrderId", payPalOrder.id())
            .param("updatedAt", Timestamp.from(now))
            .param("id", order.id())
            .update();

        String approveUrl = payPalOrder.findApproveUrl().orElse("");
        log.info("Initiated PayPal checkout for shopOrder={}: payPalOrderId={}, approveUrl={}",
            order.id(), payPalOrder.id(), approveUrl);

        return new CheckoutResponseDto(
            order.id(),
            payPalOrder.id(),
            approveUrl,
            "CREATED",
            order.totalMinor(),
            order.currency()
        );
    }

    /**
     * Captures payment for an approved order.
     * Transitions shop order to AWAITING_CONFIRMATION (NOT PAID!).
     * The order only becomes PAID when the verified webhook is processed.
     */
    @Transactional
    public CaptureOrderResponseDto capturePayPalOrder(String shopOrderId) {
        ShopOrder order = findOrderById(shopOrderId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop order not found: " + shopOrderId));

        if (order.paypalOrderId() == null || order.paypalOrderId().isBlank()) {
            throw new ValidationException("Shop order " + shopOrderId + " has no linked PayPal order ID");
        }

        log.info("Capturing PayPal order {} for shopOrder {}", order.paypalOrderId(), order.id());
        PayPalOrder capturedOrder = payPalOrdersClient.captureOrder(order.paypalOrderId());

        String captureId = capturedOrder.findFirstCaptureId().orElse("CAP-" + UUID.randomUUID().toString().substring(0, 8));

        Instant now = clock.instant();
        jdbcClient.sql("""
            UPDATE shop_orders
            SET status = 'AWAITING_CONFIRMATION', updated_at = :updatedAt
            WHERE id = :id
            """)
            .param("updatedAt", Timestamp.from(now))
            .param("id", order.id())
            .update();

        log.info("Order {} transitioned to AWAITING_CONFIRMATION after successful PayPal capture {}", order.id(), captureId);

        return new CaptureOrderResponseDto(
            order.id(),
            order.paypalOrderId(),
            captureId,
            "AWAITING_CONFIRMATION",
            order.totalMinor(),
            order.currency(),
            "Capture succeeded on PayPal. Order is AWAITING_CONFIRMATION until webhook arrives."
        );
    }

    // =========================================================================
    // Webhook Processing & Idempotent Payments
    // =========================================================================

    public Optional<PaymentRecord> findPaymentByCaptureId(String captureId) {
        Objects.requireNonNull(captureId, "captureId must not be null");
        return jdbcClient.sql("""
            SELECT id, shop_order_id, paypal_capture_id, amount_minor, currency, status, captured_at
            FROM payments
            WHERE paypal_capture_id = :captureId OR id = :captureId
            """)
            .param("captureId", captureId)
            .query((rs, rowNum) -> new PaymentRecord(
                rs.getString("id"),
                rs.getString("shop_order_id"),
                rs.getString("paypal_capture_id"),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getTimestamp("captured_at") != null ? rs.getTimestamp("captured_at").toInstant() : null
            ))
            .optional();
    }

    public Optional<PaymentRecord> findPaymentByOrderId(String shopOrderId) {
        Objects.requireNonNull(shopOrderId, "shopOrderId must not be null");
        return jdbcClient.sql("""
            SELECT id, shop_order_id, paypal_capture_id, amount_minor, currency, status, captured_at
            FROM payments
            WHERE shop_order_id = :shopOrderId
            ORDER BY captured_at DESC
            LIMIT 1
            """)
            .param("shopOrderId", shopOrderId)
            .query((rs, rowNum) -> new PaymentRecord(
                rs.getString("id"),
                rs.getString("shop_order_id"),
                rs.getString("paypal_capture_id"),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getTimestamp("captured_at") != null ? rs.getTimestamp("captured_at").toInstant() : null
            ))
            .optional();
    }

    /**
     * Applies a completed payment capture to the merchant order idempotently.
     * Transitions order from AWAITING_CONFIRMATION (or PENDING) to PAID.
     */
    @Transactional
    public boolean applyPaymentCapture(String shopOrderId, String captureId, Money amount) {
        Objects.requireNonNull(captureId, "captureId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");

        // Idempotency check 1: Payment with this capture ID already recorded
        if (findPaymentByCaptureId(captureId).isPresent()) {
            log.info("Payment capture {} already recorded. Idempotent no-op.", captureId);
            return false;
        }

        Instant now = clock.instant();
        Optional<ShopOrder> orderOpt = shopOrderId != null ? findOrderById(shopOrderId) : Optional.empty();

        // Idempotency check 2: Order already marked PAID
        if (orderOpt.isPresent() && orderOpt.get().isPaid()) {
            log.info("Shop order {} already in PAID status. Idempotent no-op.", shopOrderId);
            return false;
        }

        String targetOrderId = orderOpt.map(ShopOrder::id).orElse(null);

        // Update shop order status if order exists
        if (targetOrderId != null) {
            int updated = jdbcClient.sql("""
                UPDATE shop_orders
                SET status = 'PAID', updated_at = :now
                WHERE id = :id AND status != 'PAID'
                """)
                .param("now", Timestamp.from(now))
                .param("id", targetOrderId)
                .update();

            log.info("Updated shop order {} to PAID (rows affected: {})", targetOrderId, updated);
        } else {
            log.warn("Recording orphan capture {} without matching shop order (shopOrderId={})",
                captureId, shopOrderId);
        }

        // Insert payment record
        jdbcClient.sql("""
            INSERT INTO payments (id, shop_order_id, paypal_capture_id, amount_minor, currency, status, captured_at)
            VALUES (:id, :shopOrderId, :captureId, :amountMinor, :currency, 'COMPLETED', :capturedAt)
            """)
            .param("id", captureId)
            .param("shopOrderId", targetOrderId)
            .param("captureId", captureId)
            .param("amountMinor", amount.amountMinor())
            .param("currency", amount.currency())
            .param("capturedAt", Timestamp.from(now))
            .update();

        log.info("Recorded payment capture {} for shopOrderId={} amount={}", captureId, targetOrderId, amount);
        return true;
    }

    @Transactional
    public void markOrderPaid(String shopOrderId) {
        Instant now = clock.instant();
        jdbcClient.sql("""
            UPDATE shop_orders
            SET status = 'PAID', updated_at = :now
            WHERE id = :id
            """)
            .param("now", Timestamp.from(now))
            .param("id", shopOrderId)
            .update();
        log.info("Marked shop order {} as PAID", shopOrderId);
    }

    // =========================================================================
    // Refunds
    // =========================================================================

    @Transactional
    public RefundRecord createRefund(String shopOrderId, Long amountMinor, String reason, String source) {
        ShopOrder order = findOrderById(shopOrderId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop order not found: " + shopOrderId));

        PaymentRecord payment = findPaymentByOrderId(order.id())
            .orElseThrow(() -> new ValidationException("Cannot refund order " + shopOrderId + ": no completed payment record found"));

        long refundAmountMinor = (amountMinor != null && amountMinor > 0) ? amountMinor : payment.amountMinor();
        if (refundAmountMinor > payment.amountMinor()) {
            throw new ValidationException("Refund amount cannot exceed payment amount: " + payment.amountMinor());
        }

        String refundId = "REF-" + UUID.randomUUID().toString().substring(0, 8);
        String paypalRefundId = "PP-REF-" + UUID.randomUUID().toString().substring(0, 8);
        String refundSource = source != null && !source.isBlank() ? source.toUpperCase() : "SHOP";
        Instant now = clock.instant();

        jdbcClient.sql("""
            INSERT INTO refunds (id, payment_id, paypal_refund_id, amount_minor, currency, status, source, created_at)
            VALUES (:id, :paymentId, :paypalRefundId, :amountMinor, :currency, 'COMPLETED', :source, :createdAt)
            """)
            .param("id", refundId)
            .param("paymentId", payment.id())
            .param("paypalRefundId", paypalRefundId)
            .param("amountMinor", refundAmountMinor)
            .param("currency", payment.currency())
            .param("source", refundSource)
            .param("createdAt", Timestamp.from(now))
            .update();

        // If full refund, mark order as REFUNDED
        if (refundAmountMinor >= order.totalMinor()) {
            jdbcClient.sql("""
                UPDATE shop_orders
                SET status = 'REFUNDED', updated_at = :updatedAt
                WHERE id = :id
                """)
                .param("updatedAt", Timestamp.from(now))
                .param("id", order.id())
                .update();
            log.info("Shop order {} marked as REFUNDED", order.id());
        }

        log.info("Created refund {} for shopOrder {} (amountMinor={}, source={})",
            refundId, shopOrderId, refundAmountMinor, refundSource);

        return new RefundRecord(refundId, payment.id(), paypalRefundId, refundAmountMinor, payment.currency(), "COMPLETED", refundSource, now);
    }

    public List<RefundRecord> getRefundsByOrderId(String shopOrderId) {
        return jdbcClient.sql("""
            SELECT r.id, r.payment_id, r.paypal_refund_id, r.amount_minor, r.currency, r.status, r.source, r.created_at
            FROM refunds r
            JOIN payments p ON r.payment_id = p.id
            WHERE p.shop_order_id = :shopOrderId
            ORDER BY r.created_at DESC
            """)
            .param("shopOrderId", shopOrderId)
            .query((rs, rowNum) -> new RefundRecord(
                rs.getString("id"),
                rs.getString("payment_id"),
                rs.getString("paypal_refund_id"),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getString("source"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null
            ))
            .list();
    }

    // =========================================================================
    // Shipments (Simulated)
    // =========================================================================

    @Transactional
    public ShipmentRecord createSimulatedShipment(String shopOrderId, String carrier, String trackingRef, String status) {
        ShopOrder order = findOrderById(shopOrderId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop order not found: " + shopOrderId));

        String shipmentId = "SHIP-" + UUID.randomUUID().toString().substring(0, 8);
        String shippingCarrier = (carrier != null && !carrier.isBlank()) ? carrier : "USPS";
        String tracking = (trackingRef != null && !trackingRef.isBlank())
            ? trackingRef
            : "94001118992233" + (1000 + (int)(Math.random() * 9000));
        String shipmentStatus = (status != null && !status.isBlank()) ? status.toUpperCase() : "SHIPPED";

        Instant now = clock.instant();
        Instant deliveredAt = "DELIVERED".equalsIgnoreCase(shipmentStatus) ? now : null;

        jdbcClient.sql("""
            INSERT INTO shipments (id, shop_order_id, carrier, tracking_ref, status, delivered_at, created_at)
            VALUES (:id, :shopOrderId, :carrier, :trackingRef, :status, :deliveredAt, :createdAt)
            """)
            .param("id", shipmentId)
            .param("shopOrderId", order.id())
            .param("carrier", shippingCarrier)
            .param("trackingRef", tracking)
            .param("status", shipmentStatus)
            .param("deliveredAt", deliveredAt != null ? Timestamp.from(deliveredAt) : null)
            .param("createdAt", Timestamp.from(now))
            .update();

        log.info("Created simulated shipment {} for shopOrder {}: carrier={}, tracking={}, status={}",
            shipmentId, order.id(), shippingCarrier, tracking, shipmentStatus);

        return new ShipmentRecord(shipmentId, order.id(), shippingCarrier, tracking, shipmentStatus, deliveredAt, now);
    }

    public List<ShipmentRecord> getShipmentsByOrderId(String shopOrderId) {
        return jdbcClient.sql("""
            SELECT id, shop_order_id, carrier, tracking_ref, status, delivered_at, created_at
            FROM shipments
            WHERE shop_order_id = :shopOrderId
            ORDER BY created_at DESC
            """)
            .param("shopOrderId", shopOrderId)
            .query((rs, rowNum) -> new ShipmentRecord(
                rs.getString("id"),
                rs.getString("shop_order_id"),
                rs.getString("carrier"),
                rs.getString("tracking_ref"),
                rs.getString("status"),
                rs.getTimestamp("delivered_at") != null ? rs.getTimestamp("delivered_at").toInstant() : null,
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null
            ))
            .list();
    }
}
