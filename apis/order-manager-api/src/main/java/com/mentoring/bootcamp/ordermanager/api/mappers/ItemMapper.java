package com.mentoring.bootcamp.ordermanager.api.mappers;

import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateItemRequest;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.ItemResponse;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.UpdateItemRequest;
import com.mentoring.bootcamp.ordermanager.api.entities.ItemEntity;
import com.mentoring.bootcamp.ordermanager.api.models.Item;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ItemMapper {

    @Mapping(target = "id", ignore = true)
    Item toModel(CreateItemRequest request);

    @Mapping(target = "id", ignore = true)
    Item toModel(UpdateItemRequest request);

    ItemEntity toEntity(Item item);

    Item swallowToModel(ItemEntity itemEntity);

    List<Item> swallowToModel(List<ItemEntity> itemEntities);

    ItemResponse toResponse(Item item);

    List<ItemResponse> toResponse(List<Item> items);
}
