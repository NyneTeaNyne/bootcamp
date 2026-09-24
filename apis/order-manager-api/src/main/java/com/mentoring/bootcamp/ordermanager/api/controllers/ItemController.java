package com.mentoring.bootcamp.ordermanager.api.controllers;

import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateItemRequest;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.ItemResponse;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.UpdateItemRequest;
import com.mentoring.bootcamp.ordermanager.api.mappers.ItemMapper;
import com.mentoring.bootcamp.ordermanager.api.models.Item;
import com.mentoring.bootcamp.ordermanager.api.usecases.ItemUseCase;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/items")
@Tag(name = "Items", description = "Operations related to the product catalog")
public class ItemController {
    private final ItemUseCase itemUseCase;
    private final ItemMapper itemMapper;

    public ItemController(ItemUseCase itemUseCase, ItemMapper itemMapper) {
        this.itemUseCase = itemUseCase;
        this.itemMapper = itemMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a product to the catalog")
    @ApiResponse(responseCode = "201", description = "Item successfully created")
    @ApiResponse(responseCode = "400", description = "Invalid name or price, or product name already exists")
    public ItemResponse createItem(@RequestBody CreateItemRequest request) {
        Item itemModel = itemMapper.toModel(request);
        return itemMapper.toResponse(itemUseCase.createItem(itemModel));
    }

    @GetMapping
    @Operation(summary = "List all catalog items")
    @ApiResponse(responseCode = "200", description = "List of items (possibly empty)")
    public List<ItemResponse> getItems() {
        return itemMapper.toResponse(itemUseCase.getItems());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an item by id")
    @ApiResponse(responseCode = "200", description = "Item found")
    @ApiResponse(responseCode = "404", description = "Item not found")
    public ItemResponse getItem(@Parameter(description = "Item id", example = "1") @PathVariable("id") Integer id) {
        Item item = itemUseCase.getItem(id)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        return itemMapper.toResponse(item);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an item's name and price")
    @ApiResponse(responseCode = "200", description = "Item updated")
    @ApiResponse(responseCode = "400", description = "Invalid name or price, or product name used by another item")
    @ApiResponse(responseCode = "404", description = "Item not found")
    public ItemResponse updateItem(@Parameter(description = "Item id", example = "1") @PathVariable("id") Integer id,
                                   @RequestBody UpdateItemRequest request) {
        Item item = itemUseCase.updateItem(id, itemMapper.toModel(request))
                .orElseThrow(() -> new NotFoundException("Item not found"));
        return itemMapper.toResponse(item);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an item from the catalog")
    @ApiResponse(responseCode = "204", description = "Item deleted")
    @ApiResponse(responseCode = "404", description = "Item not found")
    public void deleteItem(@Parameter(description = "Item id", example = "1") @PathVariable("id") Integer id) {
        if (!itemUseCase.deleteItem(id)) {
            throw new NotFoundException("Item not found");
        }
    }
}
