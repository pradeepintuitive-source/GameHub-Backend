package com.gamehub.monopoly.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SimpleTile.class, name = "simple"),
        @JsonSubTypes.Type(value = Property.class, name = "property"),
        @JsonSubTypes.Type(value = Railroad.class, name = "railroad"),
        @JsonSubTypes.Type(value = Utility.class, name = "utility")
})
public sealed interface Tile permits SimpleTile, Property, Railroad, Utility {

    int position();

    String name();

    TileType tileType();
}
