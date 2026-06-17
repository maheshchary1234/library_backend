package com.lumenai.dto;

import lombok.Data;

@Data
public class AiRequest {
    private Long documentId;
    private String message;
}
