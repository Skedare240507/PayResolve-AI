package com.payresolve.shop;

import com.payresolve.common.ResourceNotFoundException;
import com.payresolve.shop.dto.CaptureOrderResponseDto;
import com.payresolve.shop.dto.CheckoutResponseDto;
import com.payresolve.shop.dto.CreateShopOrderRequest;
import com.payresolve.shop.dto.ProductDto;
import com.payresolve.shop.dto.RefundRequestDto;
import com.payresolve.shop.dto.RefundResponseDto;
import com.payresolve.shop.dto.ShipmentRequestDto;
import com.payresolve.shop.dto.ShipmentResponseDto;
import com.payresolve.shop.dto.ShopOrderDto;
import com.payresolve.shop.dto.ShopOrderItemDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller exposing the Demo Shop storefront, checkout, orders, refunds, and shipments.
 */
@RestController
@RequestMapping("/shop")
@Tag(name = "Demo Shop", description = "Storefront catalog, checkout, order lifecycle, refunds, and shipments")
public class ShopController {

    private final ShopOrderService shopOrderService;

    public ShopController(ShopOrderService shopOrderService) {
        this.shopOrderService = shopOrderService;
    }

    // =========================================================================
    // Products
    // =========================================================================

    @GetMapping("/products")
    @Operation(summary = "List catalog products available in the demo shop")
    public List<ProductDto> getProducts() {
        return shopOrderService.findAllProducts().stream()
                .map(ProductDto::from)
                .toList();
    }

    // =========================================================================
    // Orders & Checkout
    // =========================================================================

    @GetMapping("/orders")
    @Operation(summary = "List recent shop orders")
    public List<ShopOrderDto> getOrders(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        return shopOrderService.findAllOrders(limit, offset).stream()
                .map(order -> {
                    List<ShopOrderItemDto> items = shopOrderService.findOrderItems(order.id()).stream()
                            .map(ShopOrderItemDto::from)
                            .toList();
                    return ShopOrderDto.from(order, items);
                })
                .toList();
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Get single shop order details")
    public ShopOrderDto getOrderById(@PathVariable String id) {
        ShopOrder order = shopOrderService.findOrderById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shop order not found: " + id));
        List<ShopOrderItemDto> items = shopOrderService.findOrderItems(order.id()).stream()
                .map(ShopOrderItemDto::from)
                .toList();
        return ShopOrderDto.from(order, items);
    }

    @PostMapping("/orders")
    @Operation(summary = "Create a new shop order from cart items")
    public ResponseEntity<ShopOrderDto> createOrder(@RequestBody CreateShopOrderRequest request) {
        ShopOrder order = shopOrderService.createOrderFromCart(
                request.items(),
                request.customerLabel(),
                request.currency()
        );
        List<ShopOrderItemDto> items = shopOrderService.findOrderItems(order.id()).stream()
                .map(ShopOrderItemDto::from)
                .toList();
        return ResponseEntity.ok(ShopOrderDto.from(order, items));
    }

    @PostMapping("/orders/{id}/checkout")
    @Operation(summary = "Initiate PayPal Sandbox checkout for a shop order")
    public ResponseEntity<CheckoutResponseDto> initiateCheckout(
            @PathVariable String id,
            @RequestParam(required = false) String returnUrl,
            @RequestParam(required = false) String cancelUrl
    ) {
        CheckoutResponseDto response = shopOrderService.initiatePayPalCheckout(id, returnUrl, cancelUrl);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/orders/{id}/capture")
    @Operation(summary = "Capture an approved PayPal order server-side (transitions to AWAITING_CONFIRMATION)")
    public ResponseEntity<CaptureOrderResponseDto> captureOrder(@PathVariable String id) {
        CaptureOrderResponseDto response = shopOrderService.capturePayPalOrder(id);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Refunds
    // =========================================================================

    @PostMapping("/orders/{id}/refund")
    @Operation(summary = "Issue a refund for a shop order")
    public ResponseEntity<RefundResponseDto> createRefund(
            @PathVariable String id,
            @RequestBody(required = false) RefundRequestDto request
    ) {
        Long amountMinor = request != null ? request.amountMinor() : null;
        String reason = request != null ? request.reason() : "Customer requested refund";
        RefundRecord refund = shopOrderService.createRefund(id, amountMinor, reason, "SHOP");
        return ResponseEntity.ok(RefundResponseDto.from(refund));
    }

    @GetMapping("/orders/{id}/refunds")
    @Operation(summary = "List refunds for a shop order")
    public List<RefundResponseDto> getRefunds(@PathVariable String id) {
        return shopOrderService.getRefundsByOrderId(id).stream()
                .map(RefundResponseDto::from)
                .toList();
    }

    // =========================================================================
    // Shipments (Simulated)
    // =========================================================================

    @PostMapping("/orders/{id}/shipments")
    @Operation(summary = "Create a simulated fulfillment shipment for an order")
    public ResponseEntity<ShipmentResponseDto> createShipment(
            @PathVariable String id,
            @RequestBody(required = false) ShipmentRequestDto request
    ) {
        String carrier = request != null ? request.carrier() : "USPS";
        String trackingRef = request != null ? request.trackingRef() : null;
        String status = request != null ? request.status() : "SHIPPED";
        ShipmentRecord shipment = shopOrderService.createSimulatedShipment(id, carrier, trackingRef, status);
        return ResponseEntity.ok(ShipmentResponseDto.from(shipment));
    }

    @GetMapping("/orders/{id}/shipments")
    @Operation(summary = "Get shipments for a shop order")
    public List<ShipmentResponseDto> getShipments(@PathVariable String id) {
        return shopOrderService.getShipmentsByOrderId(id).stream()
                .map(ShipmentResponseDto::from)
                .toList();
    }
}
