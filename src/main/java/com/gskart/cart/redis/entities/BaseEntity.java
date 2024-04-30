package com.gskart.cart.redis.entities;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.OffsetDateTime;

@Data
public class BaseEntity {
    @Id
    private String id;
    private String mongoObjectId;
    private String createdBy;
    private OffsetDateTime createdOn;
    private  String modifiedBy;
    private OffsetDateTime modifiedOn;
}
