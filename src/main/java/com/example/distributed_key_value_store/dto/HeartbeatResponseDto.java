package com.example.distributed_key_value_store.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HeartbeatResponseDto {
    private boolean success;
    private int term;
}
