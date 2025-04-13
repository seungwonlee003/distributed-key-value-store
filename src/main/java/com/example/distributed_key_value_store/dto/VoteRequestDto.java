package com.example.distributed_key_value_store.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VoteRequestDto {
    private int term;
    private int candidateId;
    private int lastLogIndex;
    private int lastLogTerm;
}
