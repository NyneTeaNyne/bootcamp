package com.mentoring.bootcamp.ordermanager.api.controllers;

import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateOrderRequest;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.OrderResponse;
import com.mentoring.bootcamp.ordermanager.api.mappers.OrderMapper;
import com.mentoring.bootcamp.ordermanager.api.models.Order;
import com.mentoring.bootcamp.ordermanager.api.usecases.OrderUseCase;
import com.mentoring.bootcamp.ordermanager.common.exception.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "Operations related to customer orders")
public class OrderController {
    private final OrderUseCase orderUseCase;
    private final OrderMapper orderMapper;

    public OrderController(OrderUseCase orderUseCase, OrderMapper orderMapper) {
        this.orderUseCase = orderUseCase;
        this.orderMapper = orderMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Place a new order",
            description = "Creates a PENDING order for an existing customer with a list of existing items, "
                    + "then emails a confirmation to the customer. Prices come from the catalog.")
    @ApiResponse(responseCode = "201", description = "Order successfully created and confirmation email sent")
    @ApiResponse(responseCode = "400", description = "Invalid input, or customer / item not found")
    @ApiResponse(responseCode = "500", description = "Confirmation email could not be sent: the order is not created")
    public OrderResponse createOrder(@RequestBody CreateOrderRequest request) {
        return orderMapper.toResponse(orderUseCase.createOrder(orderMapper.toModel(request)));
    }

    @GetMapping
    @Operation(summary = "List all orders", description = "Returns every order with its customer, lines and total")
    @ApiResponse(responseCode = "200", description = "List of orders (possibly empty)")
    public List<OrderResponse> getOrders() {
        return orderMapper.toResponse(orderUseCase.getOrders());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order by id")
    @ApiResponse(responseCode = "200", description = "Order found")
    @ApiResponse(responseCode = "404", description = "Order not found")
    public OrderResponse getOrder(@Parameter(description = "Order id", example = "1") @PathVariable("id") Integer id) {
        Order order = orderUseCase.getOrder(id)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return orderMapper.toResponse(order);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an order", description = "Deletes the order and its lines; customer and items are kept")
    @ApiResponse(responseCode = "204", description = "Order deleted")
    @ApiResponse(responseCode = "404", description = "Order not found")
    public void deleteOrder(@Parameter(description = "Order id", example = "1") @PathVariable("id") Integer id) {
        if (!orderUseCase.deleteOrder(id)) {
            throw new NotFoundException("Order not found");
        }
    }
}
