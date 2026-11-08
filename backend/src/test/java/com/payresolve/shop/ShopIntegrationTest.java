package com.payresolve.shop;

import com.payresolve.common.Money;
import com.payresolve.paypal.PayPalOrdersClient;
import com.payresolve.paypal.model.Amount;
import com.payresolve.paypal.model.CaptureSummary;
import com.payresolve.paypal.model.LinkDescription;
import com.payresolve.paypal.model.PayPalOrder;
import com.payresolve.paypal.model.PaymentCollection;
import com.payresolve.paypal.model.PurchaseUnit;
import com.payresolve.shop.dto.CreateShopOrderRequest;
import com.payresolve.shop.dto.OrderItemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ShopIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShopOrderService shopOrderService;

    @Autowired
    private DemoFaultSwitches faultSwitches;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PayPalOrdersClient payPalOrdersClient;

    @BeforeEach
    void setUp() {
        faultSwitches.reset();
        jdbcClient.sql("DELETE FROM shipments").update();
        jdbcClient.sql("DELETE FROM refunds").update();
        jdbcClient.sql("DELETE FROM payments").update();
        jdbcClient.sql("DELETE FROM shop_order_items").update();
        jdbcClient.sql("DELETE FROM shop_orders").update();
    }

    @Test
    @DisplayName("1. Product catalog: GET /api/shop/products returns the 3 seeded products")
    void shouldReturnSeededProducts() throws Exception {
        mockMvc.perform(get("/api/shop/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.id == 'mug')].name").value("Ceramic mug"))
                .andExpect(jsonPath("$[?(@.id == 'mug')].priceMinor").value(2400))
                .andExpect(jsonPath("$[?(@.id == 'tote')].name").value("Canvas tote"))
                .andExpect(jsonPath("$[?(@.id == 'tote')].priceMinor").value(3850))
                .andExpect(jsonPath("$[?(@.id == 'notebook')].name").value("Notebook set"))
                .andExpect(jsonPath("$[?(@.id == 'notebook')].priceMinor").value(2200));
    }

    @Test
    @DisplayName("2. Order creation: Creates order in PENDING status with correctly calculated minor units total and persists line items")
    void shouldCreateOrderFromCart() throws Exception {
        CreateShopOrderRequest request = new CreateShopOrderRequest(
                List.of(
                        new OrderItemRequest("mug", 1),
                        new OrderItemRequest("tote", 1)
                ),
                "Alice Tester",
                "USD"
        );

        mockMvc.perform(post("/api/shop/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalMinor").value(6250)) // $24.00 + $38.50 = $62.50
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.customerLabel").value("Alice Tester"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[?(@.productId == 'mug')].quantity").value(1))
                .andExpect(jsonPath("$.items[?(@.productId == 'tote')].quantity").value(1));

        List<ShopOrder> orders = shopOrderService.findAllOrders(10, 0);
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).status()).isEqualTo("PENDING");
        assertThat(orders.get(0).totalMinor()).isEqualTo(6250L);

        List<ShopOrderItem> persistedItems = shopOrderService.findOrderItems(orders.get(0).id());
        assertThat(persistedItems).hasSize(2);
        assertThat(shopOrderService.verifyOrderTotalMatchesItems(orders.get(0).id())).isTrue();
    }

    @Test
    @DisplayName("3. Checkout & Capture: captureOrder moves status to AWAITING_CONFIRMATION, moves to PAID only when webhook arrives")
    void shouldFollowCheckoutAndCaptureLifecycle() throws Exception {
        // Step 1: Create Order
        ShopOrder order = shopOrderService.createOrderFromCart(
                List.of(new OrderItemRequest("mug", 1)),
                "Bob Buyer",
                "USD"
        );
        assertThat(order.status()).isEqualTo("PENDING");

        // Step 2: Initiate PayPal Checkout
        PayPalOrder mockPayPalCreated = new PayPalOrder(
                "PP-ORD-999",
                "CREATED",
                "CAPTURE",
                List.of(new PurchaseUnit("PP-ORD-999", order.id(), new Amount("USD", "24.00"), null)),
                List.of(new LinkDescription("https://www.sandbox.paypal.com/checkoutnow?token=PP-ORD-999", "approve", "GET"))
        );
        when(payPalOrdersClient.createOrder(eq(order.id()), any(), any(), any())).thenReturn(mockPayPalCreated);

        mockMvc.perform(post("/api/shop/orders/" + order.id() + "/checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopOrderId").value(order.id()))
                .andExpect(jsonPath("$.paypalOrderId").value("PP-ORD-999"))
                .andExpect(jsonPath("$.approveUrl").value("https://www.sandbox.paypal.com/checkoutnow?token=PP-ORD-999"));

        // Step 3: Capture order server-side
        CaptureSummary captureSummary = new CaptureSummary("CAP-999", "COMPLETED", new Amount("USD", "24.00"), null, null, null, null);
        PaymentCollection payments = new PaymentCollection(List.of(captureSummary), List.of());
        PayPalOrder mockPayPalCaptured = new PayPalOrder(
                "PP-ORD-999",
                "COMPLETED",
                "CAPTURE",
                List.of(new PurchaseUnit("PP-ORD-999", order.id(), new Amount("USD", "24.00"), payments)),
                List.of()
        );
        when(payPalOrdersClient.captureOrder("PP-ORD-999")).thenReturn(mockPayPalCaptured);

        mockMvc.perform(post("/api/shop/orders/" + order.id() + "/capture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.captureId").value("CAP-999"));

        // Verify order is strictly AWAITING_CONFIRMATION (NOT PAID!)
        ShopOrder awaitingOrder = shopOrderService.findOrderById(order.id()).orElseThrow();
        assertThat(awaitingOrder.status()).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(awaitingOrder.isPaid()).isFalse();

        // Step 4: Webhook arrives and processes payment
        boolean effectApplied = shopOrderService.applyPaymentCapture(order.id(), "CAP-999", Money.parse("24.00", "USD"));
        assertThat(effectApplied).isTrue();

        // Order is now PAID
        ShopOrder paidOrder = shopOrderService.findOrderById(order.id()).orElseThrow();
        assertThat(paidOrder.status()).isEqualTo("PAID");
        assertThat(paidOrder.isPaid()).isTrue();
    }

    @Test
    @DisplayName("4. Refunds: POST /api/shop/orders/{id}/refund creates refund record and updates order status")
    void shouldProcessRefund() throws Exception {
        ShopOrder order = shopOrderService.createOrderFromCart(
                List.of(new OrderItemRequest("notebook", 1)),
                "Charlie Refund",
                "USD"
        );
        shopOrderService.applyPaymentCapture(order.id(), "CAP-REF-1", Money.parse("22.00", "USD"));

        mockMvc.perform(post("/api/shop/orders/" + order.id() + "/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "amountMinor": 2200, "reason": "Customer returned item" }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amountMinor").value(2200))
                .andExpect(jsonPath("$.source").value("SHOP"));

        // Verify order status updated to REFUNDED
        ShopOrder refundedOrder = shopOrderService.findOrderById(order.id()).orElseThrow();
        assertThat(refundedOrder.status()).isEqualTo("REFUNDED");

        // Query refunds list endpoint
        mockMvc.perform(get("/api/shop/orders/" + order.id() + "/refunds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentId").value("CAP-REF-1"))
                .andExpect(jsonPath("$[0].amountMinor").value(2200));
    }

    @Test
    @DisplayName("5. Shipments: POST /api/shop/orders/{id}/shipments creates simulated shipment")
    void shouldCreateAndRetrieveShipments() throws Exception {
        ShopOrder order = shopOrderService.createOrderFromCart(
                List.of(new OrderItemRequest("tote", 1)),
                "Dave Shipment",
                "USD"
        );

        mockMvc.perform(post("/api/shop/orders/" + order.id() + "/shipments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "carrier": "FedEx", "trackingRef": "FX-12345678", "status": "SHIPPED" }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carrier").value("FedEx"))
                .andExpect(jsonPath("$.trackingRef").value("FX-12345678"))
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        mockMvc.perform(get("/api/shop/orders/" + order.id() + "/shipments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].carrier").value("FedEx"))
                .andExpect(jsonPath("$[0].status").value("SHIPPED"));
    }

    @Test
    @DisplayName("6. Database-backed fault switches: update, persist to DB, reset, and alter order total")
    void shouldManageDatabaseBackedFaultSwitches() throws Exception {
        // Initial state
        mockMvc.perform(get("/api/demo/faults"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failWebhookHandler").value(false))
                .andExpect(jsonPath("$.dropWebhook").value(false))
                .andExpect(jsonPath("$.alterOrderTotal").value(false))
                .andExpect(jsonPath("$.demoMode").value(true));

        // Update fault switches
        mockMvc.perform(post("/api/demo/faults")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "failWebhookHandler": true,
                        "dropWebhook": true,
                        "alterOrderTotal": true,
                        "duplicateDelivery": true
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failWebhookHandler").value(true))
                .andExpect(jsonPath("$.dropWebhook").value(true))
                .andExpect(jsonPath("$.alterOrderTotal").value(true))
                .andExpect(jsonPath("$.duplicateDelivery").value(true));

        // Verify persisted in PostgreSQL / H2 table demo_fault_switches
        Boolean dbFail = jdbcClient.sql("SELECT fail_webhook_handler FROM demo_fault_switches WHERE id = 'DEFAULT'")
                .query(Boolean.class)
                .single();
        assertThat(dbFail).isTrue();

        // Verify alterOrderTotal fault switch alters cart total upon order creation
        ShopOrder alteredOrder = shopOrderService.createOrderFromCart(
                List.of(new OrderItemRequest("mug", 1)),
                "Altered Buyer",
                "USD"
        );
        // mug is 2400 minor ($24.00), altered total has +1000 minor ($10.00) = 3400 minor ($34.00)
        assertThat(alteredOrder.totalMinor()).isEqualTo(3400L);

        // Reset endpoint
        mockMvc.perform(post("/api/demo/faults/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failWebhookHandler").value(false))
                .andExpect(jsonPath("$.alterOrderTotal").value(false));

        Boolean dbReset = jdbcClient.sql("SELECT alter_order_total FROM demo_fault_switches WHERE id = 'DEFAULT'")
                .query(Boolean.class)
                .single();
        assertThat(dbReset).isFalse();
    }

    @Test
    @DisplayName("7. Return/cancel URLs use the UI origin (FRONTEND_ORIGIN), not the API URL")
    void checkoutShouldUseUiOriginForReturnAndCancelUrls() {
        // Verifies the fix for the deployment bug: before the fix, return_url and cancel_url
        // pointed at the API server (APP_PUBLIC_URL), which is not a UI page. After the fix,
        // they point at the UI origin so the buyer lands on /shop/return or /shop/cancel.
        ShopOrder order = shopOrderService.createOrderFromCart(
                List.of(new OrderItemRequest("mug", 1)),
                "Return URL Test",
                "USD"
        );

        // Capture the URLs passed to PayPal via the mock
        final String[] capturedReturn = {null};
        final String[] capturedCancel = {null};

        PayPalOrder mockPayPal = new PayPalOrder(
                "PP-ORD-URL",
                "CREATED",
                "CAPTURE",
                List.of(new PurchaseUnit("PP-ORD-URL", order.id(), new Amount("USD", "24.00"), null)),
                List.of(new LinkDescription("https://sandbox.paypal.com/checkoutnow?token=PP-ORD-URL", "approve", "GET"))
        );

        when(payPalOrdersClient.createOrder(
                eq(order.id()),
                any(),
                org.mockito.ArgumentMatchers.argThat(r -> { capturedReturn[0] = r; return true; }),
                org.mockito.ArgumentMatchers.argThat(c -> { capturedCancel[0] = c; return true; })
        )).thenReturn(mockPayPal);

        shopOrderService.initiatePayPalCheckout(order.id(), null, null);

        // The return/cancel URLs must point at the UI, not the API.
        // In tests, payresolve.frontend-origin defaults to http://localhost:5173.
        assertThat(capturedReturn[0])
                .as("return_url must point at the UI /shop/return, not the API")
                .startsWith("http://localhost:5173")
                .contains("/shop/return")
                .doesNotContain(":8080");

        assertThat(capturedCancel[0])
                .as("cancel_url must point at the UI /shop/cancel, not the API")
                .startsWith("http://localhost:5173")
                .contains("/shop/cancel")
                .doesNotContain(":8080");
    }
}
